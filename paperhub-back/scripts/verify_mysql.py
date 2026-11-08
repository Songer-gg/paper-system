"""Run Java 8/MySQL integration tests and the packaged application's HTTP smoke test.

Creates its own temporary MySQL data directory and random local port. Never uses
the machine's existing database or application secrets. Requires local MySQL 8 binaries.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import urllib.parse
from database_structure import STRUCTURE_SQL, decode_inventory

FLAGS = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0


def free_port():
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        return listener.getsockname()[1]


class TemporaryMySql:
    def __init__(self, binary):
        self.binary = Path(binary).resolve()
        self.suffix = '.exe' if os.name == 'nt' else ''
        self.root = Path(tempfile.mkdtemp(prefix='paperhub-backend-it-')).resolve()
        self.data = self.root / 'data'
        self.port = free_port()
        self.process = None
        self.log = None
        self.verified = False

    def raw_sql(self, sql):
        command = [str(self.binary / ('mysql' + self.suffix)), '--no-defaults', '--protocol=TCP',
                   '--host=127.0.0.1', '--port=' + str(self.port), '--user=root', '--raw',
                   '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '--connect-timeout=1']
        return subprocess.run(command, input=sql.encode('utf-8'), capture_output=True,
                              timeout=30, creationflags=FLAGS)

    def sql(self, sql):
        if not self.verified:
            raise RuntimeError('Refusing SQL before verifying the temporary data directory')
        result = self.raw_sql(sql)
        if result.returncode:
            # Do not print input: CREATE USER contains an ephemeral password.
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))

    def __enter__(self):
        try:
            daemon = self.binary / ('mysqld' + self.suffix)
            init = subprocess.run([str(daemon), '--no-defaults', '--initialize-insecure',
                                   '--basedir=' + str(self.binary.parent), '--datadir=' + str(self.data)],
                                  capture_output=True, timeout=120, creationflags=FLAGS)
            if init.returncode:
                raise RuntimeError(init.stderr.decode('utf-8', errors='replace'))
            self.log = (self.root / 'mysql.log').open('wb')
            self.process = subprocess.Popen([str(daemon), '--no-defaults', '--console',
                                             '--basedir=' + str(self.binary.parent), '--datadir=' + str(self.data),
                                             '--bind-address=127.0.0.1', '--port=' + str(self.port),
                                             '--mysqlx=0', '--innodb-buffer-pool-size=32M'],
                                            stdout=self.log, stderr=self.log, creationflags=FLAGS)
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                if self.process.poll() is not None:
                    raise RuntimeError('Temporary MySQL exited before readiness')
                result = self.raw_sql('SELECT @@datadir;')
                if result.returncode == 0:
                    actual = Path(result.stdout.decode().strip()).resolve()
                    if actual != self.data.resolve():
                        raise RuntimeError('Unexpected MySQL data directory; refusing to modify it')
                    self.verified = True
                    return self
                time.sleep(0.3)
            raise TimeoutError('Temporary MySQL did not become ready')
        except BaseException:
            self.close()
            raise

    def __exit__(self, *args):
        self.close()

    def close(self):
        if self.verified:
            probe = self.raw_sql('SELECT @@datadir;')
            if probe.returncode == 0 and Path(probe.stdout.decode().strip()).resolve() == self.data.resolve():
                subprocess.run([str(self.binary / ('mysqladmin' + self.suffix)), '--no-defaults',
                                '--protocol=TCP', '--host=127.0.0.1', '--port=' + str(self.port),
                                '--user=root', 'shutdown'], capture_output=True, timeout=30,
                               creationflags=FLAGS)
        if self.process is not None:
            try:
                self.process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                self.process.terminate()
                self.process.wait(timeout=10)
        if self.log:
            self.log.close()
        assert self.root.parent == Path(tempfile.gettempdir()).resolve()
        assert self.root.name.startswith('paperhub-backend-it-')
        shutil.rmtree(self.root)


def smoke(backend, env):
    jar = backend / 'target' / 'paperhub-back-0.1.0-SNAPSHOT.jar'
    app_env = dict(env, PAPERHUB_PORT=str(free_port()))
    log_path = backend / 'target' / 'application-smoke.log'
    java = Path(env['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    with log_path.open('wb') as log:
        app = subprocess.Popen([str(java), '-jar', str(jar)], env=app_env, cwd=backend,
                               stdout=log, stderr=log, creationflags=FLAGS)
        try:
            deadline = time.monotonic() + 60
            url = 'http://127.0.0.1:' + app_env['PAPERHUB_PORT'] + '/api/v1/auth/login'
            body = json.dumps({'username': 'researcher', 'password': '123456', 'role': 'user'}).encode()
            while True:
                if app.poll() is not None:
                    raise RuntimeError('Packaged application exited; see target/application-smoke.log')
                try:
                    request = urllib.request.Request(url, body, {'Content-Type': 'application/json'})
                    with urllib.request.urlopen(request, timeout=3) as response:
                        result = json.load(response)
                    assert result['code'] == 0 and result['data']['user']['id'] == 1
                    assert len(result['data']['token']) > 50
                    break
                except (urllib.error.URLError, TimeoutError, ConnectionError):
                    if time.monotonic() > deadline:
                        raise TimeoutError('Packaged application HTTP login did not become ready')
                    time.sleep(0.5)
            protected = url.replace('/auth/login', '/papers/mine')
            try:
                urllib.request.urlopen(protected, timeout=3)
                raise AssertionError('Anonymous API request unexpectedly succeeded')
            except urllib.error.HTTPError as error:
                assert error.code == 401 and json.load(error)['code'] == 1001
            base = url.rsplit('/auth/login', 1)[0]
            headers = {'Authorization': 'Bearer ' + result['data']['token']}
            def api(method, path, payload=None, token=None):
                request_headers = dict(headers)
                if token:
                    request_headers['Authorization'] = 'Bearer ' + token
                body = None
                if payload is not None:
                    body = json.dumps(payload).encode()
                    request_headers['Content-Type'] = 'application/json'
                request = urllib.request.Request(base + path, body, request_headers, method=method)
                with urllib.request.urlopen(request, timeout=10) as response:
                    envelope = json.load(response)
                assert envelope['code'] == 0
                return envelope['data']
            def submit(payload, document):
                boundary = 'PaperHub' + secrets.token_hex(16)
                multipart = (('--' + boundary + '\r\nContent-Disposition: form-data; name="payload"\r\n'
                              'Content-Type: application/json\r\n\r\n').encode() + json.dumps(payload).encode() +
                             ('\r\n--' + boundary + '\r\nContent-Disposition: form-data; name="file"; '
                              'filename="smoke.pdf"\r\nContent-Type: application/pdf\r\n\r\n').encode() + document +
                             ('\r\n--' + boundary + '--\r\n').encode())
                request = urllib.request.Request(base + '/papers', multipart,
                          dict(headers, **{'Content-Type': 'multipart/form-data; boundary=' + boundary}), method='POST')
                with urllib.request.urlopen(request, timeout=10) as response:
                    return json.load(response)['data']
            marker = secrets.token_hex(8)
            category = api('POST', '/categories', {'name': 'HTTP root ' + marker, 'parentId': None})
            child = api('POST', '/categories', {'name': 'HTTP child ' + marker, 'parentId': category['id']})
            api('PATCH', '/categories/' + str(child['id']), {'name': 'HTTP renamed ' + marker})
            api('POST', '/categories/' + str(child['id']) + '/move', {'parentId': None})
            api('POST', '/categories/' + str(child['id']) + '/move', {'parentId': category['id']})
            tag = api('POST', '/tags', {'name': 'HTTP tag ' + marker, 'color': '#2563EB'})
            api('PATCH', '/tags/' + str(tag['id']), {'name': 'HTTP tag updated ' + marker, 'color': '#15803D'})
            fixture = next((Path(env['PAPERHUB_STORAGE_ROOT']) / 'papers').glob('*.pdf'))
            pdf = fixture.read_bytes()
            payload = {'title': 'HTTP smoke ' + secrets.token_hex(8), 'authors': ['Smoke author'],
                       'abstract': 'Real HTTP multipart verification', 'year': 2026, 'categoryId': child['id'],
                       'tagIds': [tag['id']], 'keywords': [], 'visibility': 'private', 'copyrightAgreed': False}
            paper_id = submit(payload, pdf)['id']
            resource = base + '/papers/' + str(paper_id)
            category_list = api('GET', '/categories')
            assert all(item['ownerId'] in (None, 1) for item in category_list)
            assert next(item for item in category_list if item['id'] == category['id'])['paperCount'] == 1
            assert next(item for item in api('GET', '/tags') if item['id'] == tag['id'])['paperCount'] == 1
            assert [item['id'] for item in api('GET', '/categories/' + str(category['id']) + '/papers')] == [paper_id]
            assert [item['id'] for item in api('GET', '/tags/' + str(tag['id']) + '/papers')] == [paper_id]
            for suffix, method, disposition in [('/file', 'GET', 'inline'), ('/download', 'POST', 'attachment')]:
                request = urllib.request.Request(resource + suffix, headers=headers, method=method)
                with urllib.request.urlopen(request, timeout=10) as response:
                    assert response.headers['Content-Type'] == 'application/pdf'
                    assert response.headers['Content-Disposition'].startswith(disposition)
                    assert response.read() == pdf
            with urllib.request.urlopen(urllib.request.Request(resource, headers=headers), timeout=10) as response:
                assert json.load(response)['data']['downloads'] == 1
            assert len(api('DELETE', '/categories/' + str(category['id']))) == 2
            assert api('DELETE', '/tags/' + str(tag['id'])) is True
            detached = api('GET', '/papers/' + str(paper_id))
            assert detached['categoryId'] is None and detached['tagIds'] == []
            with urllib.request.urlopen(urllib.request.Request(resource + '/file', headers=headers), timeout=10) as response:
                assert response.read() == pdf
            with urllib.request.urlopen(urllib.request.Request(resource, headers=headers, method='DELETE'), timeout=10) as response:
                assert json.load(response)['data'] is True
            try:
                urllib.request.urlopen(urllib.request.Request(resource + '/file', headers=headers), timeout=10)
                raise AssertionError('Deleted PDF unexpectedly remained accessible')
            except urllib.error.HTTPError as error:
                assert error.code == 404 and json.load(error)['code'] == 1003
            administrator = api('POST', '/auth/login', {'username': 'admin', 'password': 'admin123', 'role': 'admin'})
            admin_token = administrator['token']
            def admin_api(method, path, body=None):
                return api(method, path, body, token=admin_token)
            def expect_error(method, path, body, status, code, administrative=False):
                try:
                    api(method, path, body, token=admin_token if administrative else None)
                    raise AssertionError('Expected a protected business rejection')
                except urllib.error.HTTPError as error:
                    assert error.code == status and json.load(error)['code'] == code
            title = 'HTTP shared ' + marker
            def shared():
                values = dict(payload, title=title, categoryId=1, tagIds=[], visibility='shared',
                              copyrightAgreed=True, doi='10.9999/http-' + secrets.token_hex(12))
                # A valid PDF comment creates an independent byte fixture without touching real files.
                document = pdf + b'\n% PaperHub HTTP fixture ' + secrets.token_hex(16).encode() + b'\n'
                return submit(values, document)
            original = shared()
            pid = original['id']
            assert api('POST', '/papers/duplicate-check', {'doi': original['doi']})['level'] == 'doi'
            query = urllib.parse.urlencode({'title': title, 'uploader': '林', 'status': 'pending'})
            assert [item['id'] for item in admin_api('GET', '/admin/papers?' + query)] == [pid]
            assert admin_api('GET', '/admin/papers/' + str(pid))['reviewStatus'] == 'pending'
            assert pid in [item['id'] for item in admin_api('GET', '/admin/review/papers')['pending']]
            admin_api('POST', '/admin/review/papers/' + str(pid) + '/approve', {'copyrightConfirmed': True, 'comment': 'HTTP verified'})
            assert api('GET', '/shared-papers/' + str(pid))['reviewStatus'] == 'approved'
            found = api('GET', '/shared-papers?' + urllib.parse.urlencode({'q': title, 'page': 1, 'pageSize': 10}))
            assert found['total'] == 1 and found['list'][0]['id'] == pid
            assert set(api('GET', '/shared-papers/filter-options')) == {'categories', 'tags', 'years', 'journals'}
            # R1-R9 through the packaged Java 8 server, including a deleted source snapshot.
            favorite_path = '/papers/' + str(pid) + '/favorite'
            assert api('POST', favorite_path, {'favorited': True})['favorited'] is True
            first = next(row for row in api('GET', '/me/favorites') if row['paperId'] == pid)
            api('POST', favorite_path, {'favorited': True})
            again = next(row for row in api('GET', '/me/favorites') if row['paperId'] == pid)
            assert first['favoritedAt'] == again['favoritedAt'] and again['paper']['id'] == pid
            reading_path = '/papers/' + str(pid) + '/reading'
            initial = api('POST', reading_path)
            assert initial['status'] == 'unread'
            changed = api('PATCH', '/me/reading-records/' + str(pid), {'status': 'read'})
            assert changed['addedAt'] == initial['addedAt']
            assert api('POST', reading_path) == changed
            assert any(row['paperId'] == pid and row['status'] == 'read'
                       for row in api('GET', '/me/reading-records'))
            content = '  # HTTP Markdown\n\n    preserve indentation\n'
            note = api('POST', '/me/notes', {'paperId': pid, 'title': 'HTTP note ' + marker, 'content': content})
            assert note['paperTitle'] == title and note['content'] == content and note['paperAvailable'] is True
            note_path = '/me/notes/' + str(note['id'])
            private_source = submit(dict(payload, title='HTTP private note source ' + marker,
                                         categoryId=1, tagIds=[]), pdf)
            source_id = private_source['id']
            updated = api('PATCH', note_path, {'paperId': source_id, 'title': 'HTTP relinked ' + marker})
            assert updated['paperTitle'] == private_source['title'] and updated['content'] == content
            api('DELETE', '/papers/' + str(source_id))
            retained = api('GET', '/me/notes?' + urllib.parse.urlencode({'paperId': source_id, 'keyword': marker}))
            assert len(retained) == 1 and retained[0]['paperAvailable'] is False
            assert retained[0]['paperTitle'] == private_source['title']
            assert api('PATCH', note_path, {'paperId': source_id, 'content': content + 'retained'})['paperAvailable'] is False
            assert api('POST', favorite_path, {'favorited': False})['favorited'] is False
            assert any(row['paperId'] == pid for row in api('GET', '/me/reading-records'))
            assert api('DELETE', note_path) is True
            assert api('GET', '/me/notes?' + urllib.parse.urlencode({'paperId': source_id, 'keyword': marker})) == []
            admin_api('PATCH', '/admin/papers/' + str(pid) + '/offline')
            admin_api('PATCH', '/admin/papers/' + str(pid) + '/restore')
            api('PATCH', '/papers/' + str(pid) + '/review-flow', {'action': 'unshare'})
            expect_error('PATCH', '/admin/papers/' + str(pid) + '/restore', None, 400, 1006, True)
            api('PATCH', '/papers/' + str(pid) + '/review-flow', {'action': 'resubmit'})
            admin_api('POST', '/admin/review/papers/' + str(pid) + '/reject', {'comment': 'HTTP reject'})
            api('PATCH', '/papers/' + str(pid) + '/review-flow', {'action': 'resubmit'})
            admin_api('POST', '/admin/review/papers/' + str(pid) + '/approve', {'copyrightConfirmed': True})
            assert any(item['paperId'] == pid for item in admin_api('GET', '/admin/review/records?action=all'))
            linked = shared()
            pair = next(item for item in admin_api('GET', '/admin/duplicates?status=pending') if item['newPaperId'] == linked['id'])
            expect_error('POST', '/admin/review/papers/' + str(linked['id']) + '/approve', {'copyrightConfirmed': True}, 400, 1006, True)
            admin_api('POST', '/admin/duplicates/' + str(pair['id']) + '/link', {'note': 'HTTP linked'})
            assert admin_api('GET', '/admin/papers/' + str(linked['id']))['reviewStatus'] == 'rejected'
            distinct = shared()
            pair2 = next(item for item in admin_api('GET', '/admin/duplicates?status=pending') if item['newPaperId'] == distinct['id'])
            admin_api('POST', '/admin/duplicates/' + str(pair2['id']) + '/distinct', {'note': 'HTTP distinct'})
            admin_api('POST', '/admin/review/papers/' + str(distinct['id']) + '/approve', {'copyrightConfirmed': True})
            duplicate = shared()
            pair3 = next(item for item in admin_api('GET', '/admin/duplicates?status=pending') if item['newPaperId'] == duplicate['id'])
            admin_api('POST', '/admin/duplicates/' + str(pair3['id']) + '/confirm', {'note': 'HTTP duplicate'})
            assert any(item['pairId'] == pair3['id'] and item['action'] == 'duplicate' for item in admin_api('GET', '/admin/duplicate-records'))
            for item in [linked, distinct, duplicate, original]:
                admin_api('DELETE', '/admin/papers/' + str(item['id']))
            print('PASS: Java 8 HTTP login, K1-K11, PDFs, U5/U6/U10-U12, M7-M20 and all R1-R9 personal data commands')
        finally:
            app.terminate()
            try:
                app.wait(timeout=15)
            except subprocess.TimeoutExpired:
                app.kill()
                app.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--mysql-bin', required=True)
    parser.add_argument('--maven', default=shutil.which('mvn') or shutil.which('mvn.cmd'))
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    args = parser.parse_args()
    if not args.maven or not args.java_home:
        parser.error('Set --maven and --java-home or configure Maven/JAVA_HOME')
    backend = Path(__file__).resolve().parents[1]
    with TemporaryMySql(args.mysql_bin) as database:
        schema = backend.parent / 'paperhub-api' / 'db'
        database.sql((schema / 'schema.sql').read_text(encoding='utf-8'))
        (backend / 'target').mkdir(exist_ok=True)
        structure = database.raw_sql(STRUCTURE_SQL)
        if structure.returncode:
            raise RuntimeError('Temporary schema inventory failed')
        (backend / 'target/expected-database-structure.json').write_text(
            json.dumps(decode_inventory(structure.stdout.decode('utf-8')), indent=2), encoding='utf-8')
        database.sql((schema / 'seed-demo.sql').read_text(encoding='utf-8'))
        password = secrets.token_hex(20)
        database.sql("CREATE USER 'paperhub_test'@'127.0.0.1' IDENTIFIED BY '" + password + "'; "
                     "GRANT SELECT,INSERT,UPDATE,DELETE ON paperhub.* TO 'paperhub_test'@'127.0.0.1';")
        env = dict(os.environ, JAVA_HOME=str(Path(args.java_home).resolve()), DEBUG='false', TRACE='false',
                   PAPERHUB_DB_URL='jdbc:mysql://127.0.0.1:' + str(database.port) +
                   '/paperhub?connectionTimeZone=Asia/Shanghai',
                   PAPERHUB_DB_USERNAME='paperhub_test', PAPERHUB_DB_PASSWORD=password,
                   PAPERHUB_JWT_SECRET=base64.b64encode(secrets.token_bytes(32)).decode(),
                   PAPERHUB_STORAGE_ROOT=str(database.root / 'storage'),
                   PAPERHUB_CLEANUP_ENABLED='false',
                   PAPERHUB_TEST_DATA_DIR=str(database.data.resolve()))
        env['MAVEN_OPTS'] = env.get('MAVEN_OPTS', '') + ' -Dfile.encoding=UTF-8'
        (backend / 'target').mkdir(exist_ok=True)
        with (backend / 'target' / 'mysql-verification.log').open('w', encoding='utf-8') as log:
            build = subprocess.Popen([str(args.maven), '-B', '-ntp', '-Pmysql-it', 'verify'],
                                     cwd=backend, env=env, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                                     errors='replace', creationflags=FLAGS)
            for line in build.stdout:
                log.write(line)
                log.flush()
                print(line, end='', flush=True)
            code = build.wait()
        if code:
            raise RuntimeError('Maven verification failed; see target test reports')
        database.sql("USE paperhub; UPDATE `user` SET status='active',token_version=0 WHERE id IN (1,2,101); "
                     "UPDATE `user` SET role='user' WHERE id IN (1,2); UPDATE `user` SET role='admin' WHERE id=101;")
        smoke(backend, env)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    main()
