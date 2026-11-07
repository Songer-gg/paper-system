"""Rehearse a guarded legacy migration, then optionally apply it to the existing loopback DB.
Uses an already available SQL snapshot; it never creates another backup.
DDL commits implicitly: a failed partial migration must not be blindly rerun.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from database_structure import STRUCTURE_SQL, decode_inventory
from verify_mysql import TemporaryMySql

LEGACY_FINGERPRINT = '229411d317b15ce7e4fd489c965e14af27d7fbb77a84f939e8347b82c2507f6d'
CONSENT_REASON = '旧版记录缺少版权确认，请确认版权后重新提交'


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


class ExistingLocal:
    def __init__(self, binary, backend):
        config = {}
        for line in (backend / 'config/local.properties').read_text(encoding='utf-8').splitlines():
            if line.strip() and not line.lstrip().startswith('#'):
                key, value = line.split('=', 1)
                config[key.strip()] = value.strip()
        def setting(name):
            return os.environ.get(name, config.get(name, ''))
        match = re.fullmatch(r'jdbc:mysql://(127\.0\.0\.1|localhost):(\d+)/paperhub(?:\?.*)?', setting('PAPERHUB_DB_URL'))
        if not match:
            raise RuntimeError('Only the configured loopback development DB is supported')
        self.command = [str(Path(binary) / ('mysql.exe' if os.name == 'nt' else 'mysql')),
                        '--no-defaults', '--protocol=TCP', '--host='+match[1], '--port='+match[2],
                        '--user='+setting('PAPERHUB_DB_USERNAME'), '--raw', '--default-character-set=utf8mb4',
                        '--batch', '--skip-column-names']
        self.env = dict(os.environ, MYSQL_PWD=setting('PAPERHUB_DB_PASSWORD'))

    def raw_sql(self, sql):
        return subprocess.run(self.command, input=sql.encode('utf-8'), capture_output=True,
                              env=self.env, timeout=120,
                              creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)

    def sql(self, sql):
        result = self.raw_sql(sql)
        if result.returncode:
            raise RuntimeError('Local migration failed. Stop and inspect its stage before any retry.')


def query(database, sql):
    result = database.raw_sql(sql)
    if result.returncode:
        # Do not print SQL or rows from a personal database.
        raise RuntimeError('Migration validation query failed')
    return result.stdout.decode('utf-8')


def structure(database):
    return decode_inventory(query(database, STRUCTURE_SQL))


def rows(database, legacy_structure, migrated=False):
    tables = {}
    for name in legacy_structure:
        if name.startswith('column:'):
            _, table, column = name.split(':')
            tables.setdefault(table, []).append(column)
    result = {}
    renames = {'old_paper_id': 'original_paper_id', 'similarity': 'title_similarity'}
    for table, columns in sorted(tables.items()):
        parts = []
        for column in columns:
            actual = renames.get(column, column) if migrated and table == 'duplicate_pair' else column
            parts.extend(["'"+column+"'", '`'+actual+'`'])
        sort = 'paper_id,tag_id' if table == 'paper_tag' else 'id'
        raw = query(database, 'SELECT JSON_OBJECT('+','.join(parts)+') FROM paperhub.`'+table+'` ORDER BY '+sort+';')
        result[table] = [json.loads(line) for line in raw.splitlines()]
    return result


def expected_rows(before):
    expected = json.loads(json.dumps(before))
    titles = {p['id']: p['title'] for p in expected['paper']}
    for paper in expected['paper']:
        if paper['visibility'] == 'private':
            paper['review_status'] = None
        elif not paper['copyright_agreed']:
            paper['review_status'] = 'rejected'
            paper['review_comment'] = paper['review_comment'] or CONSENT_REASON
    for report in expected['report']:
        if report['paper_title'] is None:
            report['paper_title'] = titles[report['paper_id']]
    return expected


def verify_rows(before, after):
    expected = expected_rows(before)
    require(len(after['operation_log']) == len(expected['operation_log']) + 1, 'Migration audit is required')
    after = dict(after, operation_log=after['operation_log'][:-1])
    changed = []
    for table in expected:
        if len(expected[table]) != len(after[table]):
            changed.append(table + ':rowCount')
            continue
        for old, new in zip(expected[table], after[table]):
            changed.extend(table + ':' + field for field in old if old[field] != new[field])
    require(not changed, 'Unexpected changed fields (no values): ' + ','.join(sorted(set(changed))))


def preflight(before):
    for paper in before['paper']:
        require(paper['abstract'] is not None and paper['year'] is not None and paper['keywords'] is not None, 'Missing required metadata')
        authors = paper['authors']
        keywords = paper['keywords']
        if isinstance(authors, str):
            authors = json.loads(authors)
        if isinstance(keywords, str):
            keywords = json.loads(keywords)
        require(isinstance(authors, list) and authors and all(isinstance(x, str) for x in authors), 'Invalid authors')
        require(isinstance(keywords, list) and all(isinstance(x, str) for x in keywords), 'Invalid keywords')
        require(paper['visibility'] in ('private', 'shared'), 'Unknown visibility')
        if paper['visibility'] == 'shared':
            require(paper['review_status'] in ('pending', 'approved', 'rejected'), 'Offline provenance requires manual classification')
    for pair in before['duplicate_pair']:
        require(pair['old_paper_id'] and pair['new_paper_id'], 'Missing historical paper association')
    require(all(r['paper_id'] for r in before['report']), 'Missing historical paper association')


def main():
    backend = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument('--mysql-bin', required=True)
    parser.add_argument('--snapshot', type=Path, help='Already available SQL snapshot; no new backup is made')
    parser.add_argument('--apply', action='store_true', help='Apply only after successful isolated rehearsal')
    args = parser.parse_args()
    local = ExistingLocal(args.mysql_bin, backend)
    expected = json.loads((backend / 'target/expected-database-structure.json').read_text(encoding='utf-8'))
    legacy = structure(local)
    if legacy == expected:
        print('Database is already v1.1; no changes made')
        return
    require(fingerprint(legacy) == LEGACY_FINGERPRINT, 'Unsupported or partially migrated schema; refusing changes')
    snapshot = args.snapshot or Path((backend / 'target/local-backup-path.txt').read_text(encoding='utf-8'))
    migration = (backend.parent / 'paperhub-api/db/migrations/v1.0_to_v1.1.sql').read_text(encoding='utf-8')
    before = rows(local, legacy)
    preflight(before)
    with TemporaryMySql(args.mysql_bin) as clone:
        clone.sql(snapshot.read_text(encoding='utf-8'))
        require(structure(clone) == legacy, 'Snapshot structure differs from current DB')
        require(rows(clone, legacy) == before, 'Snapshot rows differ from current DB')
        clone.sql(migration)
        actual = structure(clone)
        differences = [key for key in sorted(set(actual) | set(expected)) if actual.get(key) != expected.get(key)]
        if differences:
            print('Rehearsal schema differences:', differences)
            raise RuntimeError('Isolated rehearsal did not match the canonical schema')
        verify_rows(before, rows(clone, legacy, migrated=True))
    report = {'rehearsed': True, 'schemaMatches': True, 'originalRowsPreserved': True,
              'sourceFingerprint': fingerprint(legacy), 'applied': False,
              'papersMissingConsent': sum(p['visibility'] == 'shared' and not p['copyright_agreed'] for p in before['paper']),
              'rowCountsBefore': {table: len(data) for table, data in before.items()}}
    (backend / 'target/migration-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    if args.apply:
        require(structure(local) == legacy and rows(local, legacy) == before, 'Database changed during rehearsal')
        local.sql(migration)
        require(structure(local) == expected, 'Post-migration schema mismatch; stop before starting the app')
        verify_rows(before, rows(local, legacy, migrated=True))
        report['applied'] = True
        (backend / 'target/migration-verification.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(json.dumps(report))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    main()

