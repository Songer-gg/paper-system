"""Query OSV for resolved Maven runtime dependencies; writes a reviewable report.

First generate target/dependencies.tgf using Maven dependency:tree -DoutputType=tgf.
Only public dependency coordinates are sent. No source code, credentials, or tokens.
Findings are release gates to assess, not automatic dependency upgrades.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
from pathlib import Path
import urllib.request


def request_json(url, body=None):
    data = None if body is None else json.dumps(body).encode()
    request = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--tree', default='target/dependencies.tgf')
    parser.add_argument('--output', default='target/dependency-audit.json')
    args = parser.parse_args()
    dependencies = []
    for line in Path(args.tree).read_text(encoding='utf-8').splitlines():
        if line.strip() == '#':
            break
        fields = line.split(maxsplit=1)
        if len(fields) != 2:
            continue
        coordinate = fields[1].split(':')
        if len(coordinate) < 5 or coordinate[-1] not in ('compile', 'runtime'):
            continue
        dependencies.append({'package': {'ecosystem': 'Maven', 'name': ':'.join(coordinate[:2])},
                             'version': coordinate[-2]})
    if not dependencies:
        raise RuntimeError('No resolved runtime dependencies found')
    findings = []
    try:
        for start in range(0, len(dependencies), 100):
            batch = dependencies[start:start + 100]
            response = request_json('https://api.osv.dev/v1/querybatch', {'queries': batch})
            results = response['results']
            if len(results) != len(batch):
                raise RuntimeError('Incomplete OSV batch response')
            for dependency, result in zip(batch, results):
                if result.get('vulns'):
                    findings.append(dict(dependency, ids=[item['id'] for item in result['vulns']]))
        ids = sorted({item for finding in findings for item in finding['ids']})
        with ThreadPoolExecutor(max_workers=6) as pool:
            details = list(pool.map(lambda item: request_json('https://api.osv.dev/v1/vulns/' + item), ids))
        report = {'complete': True, 'checkedAt': datetime.now(timezone.utc).isoformat(),
                  'runtimeDependencies': len(dependencies), 'affectedDependencies': findings,
                  'advisories': details}
    except Exception as error:
        report = {'complete': False, 'checkedAt': datetime.now(timezone.utc).isoformat(),
                  'runtimeDependencies': len(dependencies), 'error': str(error), 'partialFindings': findings}
    Path(args.output).write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Dependency audit complete=' + str(report['complete']) + '; runtime dependencies=' + str(len(dependencies)))
    if report['complete']:
        print('Affected dependencies=' + str(len(findings)) + '; advisories=' + str(len(report['advisories'])))
    else:
        raise SystemExit(2)


if __name__ == '__main__':
    main()
