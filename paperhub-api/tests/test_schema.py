"""在独立临时 MySQL 实例验证 DDL、种子、约束和历史保留，不连接用户已有库。

运行：python -B paperhub-api/tests/test_schema.py --bin-dir "D:/Program Files/MySQL/MySQL Server 8.0/bin"
"""
import argparse
import concurrent.futures
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--bin-dir', required=True)
    args = parser.parse_args()
    binary = Path(args.bin_dir).resolve()
    suffix = '.exe' if os.name == 'nt' else ''
    daemon = binary / ('mysqld' + suffix)
    client = binary / ('mysql' + suffix)
    flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
    root = Path(tempfile.mkdtemp(prefix='paperhub-schema-')).resolve()
    data = root / 'data'
    process = None
    log = None
    command = None
    checks = 0
    try:
        result = subprocess.run([str(daemon), '--no-defaults', '--initialize-insecure',
                                 '--basedir=' + str(binary.parent), '--datadir=' + str(data)],
                                capture_output=True, timeout=120, creationflags=flags)
        if result.returncode:
            raise RuntimeError(result.stderr.decode('utf-8', errors='replace'))
        with socket.socket() as listener:
            listener.bind(('127.0.0.1', 0))
            port = listener.getsockname()[1]
        log = (root / 'server.log').open('wb')
        process = subprocess.Popen([str(daemon), '--no-defaults', '--console',
                                    '--basedir=' + str(binary.parent), '--datadir=' + str(data),
                                    '--bind-address=127.0.0.1', '--port=' + str(port),
                                    '--mysqlx=0', '--innodb-buffer-pool-size=32M'],
                                   stdout=log, stderr=log, creationflags=flags)
        command = [str(client), '--no-defaults', '--protocol=TCP', '--host=127.0.0.1',
                   '--port=' + str(port), '--user=root', '--default-character-set=utf8mb4',
                   '--batch', '--skip-column-names', '--connect-timeout=1']

        def sql(text, expected=True):
            nonlocal checks
            result = subprocess.run(command, input=text.encode('utf-8'), capture_output=True,
                                    timeout=30, creationflags=flags)
            if expected is not None:
                checks += 1
                if (result.returncode == 0) != expected:
                    raise AssertionError(result.stderr.decode('utf-8', errors='replace') + '\nSQL: ' + text[:150])
            return result

        deadline = time.monotonic() + 60
        while True:
            if process.poll() is not None:
                raise RuntimeError((root / 'server.log').read_text(encoding='utf-8', errors='replace'))
            result = sql('SELECT @@datadir;', expected=None)
            if result.returncode == 0:
                actual = Path(result.stdout.decode().strip()).resolve()
                if actual != data.resolve():
                    raise RuntimeError('Refusing to modify unexpected MySQL instance: ' + str(actual))
                break
            if time.monotonic() >= deadline:
                raise TimeoutError('Temporary MySQL startup timed out')
            time.sleep(0.3)

        db = Path(__file__).resolve().parents[1] / 'db'
        sql((db / 'schema.sql').read_text(encoding='utf-8'))
        sql((db / 'seed-demo.sql').read_text(encoding='utf-8'))
        # 平台根、平台子级、自有根和标签均须真正防重，不能只依赖先查后插。
        sql("USE paperhub; INSERT INTO category(name,parent_id) VALUES('人工智能',NULL);", False)
        sql("USE paperhub; INSERT INTO category(name,parent_id) VALUES('机器学习',1);", False)
        sql("USE paperhub; INSERT INTO category(name,custom,owner_id) VALUES('personal',1,1);")
        sql("USE paperhub; INSERT INTO category(name,custom,owner_id) VALUES('personal',1,1);", False)
        sql("USE paperhub; INSERT INTO category(name,custom,owner_id) VALUES('personal',1,2);")
        sql("USE paperhub; INSERT INTO tag(name,color) VALUES('深度学习','#000000');", False)
        sql("USE paperhub; INSERT INTO tag(name,color,custom) VALUES('bad','#000000',1);", False)

        def insert(paper_id, visibility='shared', doi='test/same', digest='a' * 64):
            status = "'pending'" if visibility == 'shared' else 'NULL'
            return ("USE paperhub; INSERT INTO paper(id,title,authors,abstract,year,keywords,visibility,"
                    "uploader_id,review_status,doi_normalized,file_sha256) VALUES(" + str(paper_id) +
                    ",'test',JSON_ARRAY('author'),'abstract',2026,JSON_ARRAY(),'" + visibility +
                    "',1," + status + ",'" + doi + "','" + digest + "');")

        sql(insert(9001))
        sql(insert(9002, digest='b' * 64), False)
        sql(insert(9003, doi='test/different'), False)
        sql(insert(9004, 'private'))
        sql(insert(9005, 'private'))
        sql("USE paperhub; UPDATE paper SET review_status='pending' WHERE id=9004;", False)
        sql("USE paperhub; UPDATE paper SET review_status='offline' WHERE id=9001;", False)
        sql("USE paperhub; UPDATE paper SET review_status='offline',offline_reason='withdrawn' WHERE id=9001;")
        sql(insert(9006, digest='c' * 64), False)
        sql("USE paperhub; UPDATE paper SET deleted_at=NOW() WHERE id=9001;")
        sql(insert(9007))
        sql("USE paperhub; UPDATE paper SET authors=JSON_OBJECT() WHERE id=9007;", False)
        # 两个独立连接竞争同一共享标识，只能一个成功。
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda i: sql(insert(i, doi='race/doi', digest='d' * 64), None).returncode,
                                    [9010, 9011]))
        assert sorted(results) == [0, 1], results
        checks += 1
        sql("USE paperhub; DELETE FROM paper WHERE id=2001;", False)
        sql("USE paperhub; UPDATE paper SET deleted_at=NOW() WHERE id=2001;")
        result = sql("USE paperhub; SELECT COUNT(*) FROM note WHERE paper_id=2001;")
        assert result.stdout.decode().strip() == '1'
        sql("USE paperhub; INSERT INTO duplicate_pair(original_paper_id,new_paper_id,original_paper_title,"
            "new_paper_title,title_similarity,reason) VALUES(2001,2001,'a','a',50,'self');", False)
        print('PASS: isolated MySQL schema/seed and ' + str(checks) + ' constraint checks')
    finally:
        if command is not None:
            # Windows mysqld 会启动监督子进程，必须让实际服务正常退出后再删除数据目录。
            probe = subprocess.run(command, input=b'SELECT @@datadir;', capture_output=True,
                                   timeout=5, creationflags=flags)
            if probe.returncode == 0 and Path(probe.stdout.decode().strip()).resolve() == data.resolve():
                subprocess.run([str(binary / ('mysqladmin' + suffix)), '--no-defaults',
                                '--protocol=TCP', '--host=127.0.0.1', '--port=' + str(port),
                                '--user=root', 'shutdown'], capture_output=True, timeout=30,
                               creationflags=flags)
        if process is not None and process.poll() is None:
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.terminate()
                process.kill()
                process.wait(timeout=10)
        if log:
            log.close()
        # 只清理本测试创建的临时目录，先核对绝对路径范围。
        assert root.parent == Path(tempfile.gettempdir()).resolve()
        assert root.name.startswith('paperhub-schema-')
        shutil.rmtree(root)


if __name__ == '__main__':
    main()
