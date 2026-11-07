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
            print('PASS: packaged Java 8 application, real HTTP login and anonymous 401')
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
        database.sql((schema / 'seed-demo.sql').read_text(encoding='utf-8'))
        password = secrets.token_hex(20)
        database.sql("CREATE USER 'paperhub_test'@'127.0.0.1' IDENTIFIED BY '" + password + "'; "
                     "GRANT SELECT,INSERT,UPDATE,DELETE ON paperhub.* TO 'paperhub_test'@'127.0.0.1';")
        env = dict(os.environ, JAVA_HOME=str(Path(args.java_home).resolve()), DEBUG='false', TRACE='false',
                   PAPERHUB_DB_URL='jdbc:mysql://127.0.0.1:' + str(database.port) +
                   '/paperhub?connectionTimeZone=Asia/Shanghai',
                   PAPERHUB_DB_USERNAME='paperhub_test', PAPERHUB_DB_PASSWORD=password,
                   PAPERHUB_JWT_SECRET=base64.b64encode(secrets.token_bytes(32)).decode(),
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
        smoke(backend, env)


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    main()
