"""Check an existing loopback development database without changing structure or data."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from database_structure import STRUCTURE_SQL, decode_inventory


def main():
    backend = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument('--mysql-bin', required=True)
    parser.add_argument('--config', type=Path, default=backend / 'config/local.properties')
    args = parser.parse_args()
    config = {}
    for line in args.config.read_text(encoding='utf-8').splitlines():
        if line.strip() and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            config[key.strip()] = value.strip()
    def setting(name):
        return os.environ.get(name, config.get(name, ''))
    match = re.fullmatch(r'jdbc:mysql://(127\.0\.0\.1|localhost):(\d+)/paperhub(?:\?.*)?',
                         setting('PAPERHUB_DB_URL'))
    if not match:
        raise RuntimeError('Only an explicit loopback paperhub development URL is accepted')
    expected_path = backend / 'target/expected-database-structure.json'
    expected = json.loads(expected_path.read_text(encoding='utf-8'))
    env = dict(os.environ, MYSQL_PWD=setting('PAPERHUB_DB_PASSWORD'))
    mysql = Path(args.mysql_bin) / ('mysql.exe' if os.name == 'nt' else 'mysql')
    result = subprocess.run([str(mysql), '--no-defaults', '--protocol=TCP', '--host='+match[1],
                             '--port='+match[2], '--user='+setting('PAPERHUB_DB_USERNAME'),
                             '--raw', '--default-character-set=utf8mb4', '--batch', '--skip-column-names'],
                            input=('SET SESSION TRANSACTION READ ONLY; START TRANSACTION;\n'+STRUCTURE_SQL+
                                   '\nROLLBACK;').encode('utf-8'), env=env, capture_output=True, timeout=30)
    if result.returncode:
        # Do not echo client errors or config values; callers can inspect connection settings locally.
        raise RuntimeError('Read-only local MySQL connection/inventory failed')
    actual = decode_inventory(result.stdout.decode('utf-8'))
    differences = [key for key in sorted(set(actual) | set(expected)) if actual.get(key) != expected.get(key)]
    report = {'readOnly': True, 'schemaMatches': not differences, 'comparedObjects': len(expected),
              'actualObjects': len(actual), 'differences': differences}
    (backend / 'target/local-database-check.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))
    if differences:
        raise SystemExit('Schema differences found; prepare a migration before changing the existing database')


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    main()

