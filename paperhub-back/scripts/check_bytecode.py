"""Check resolved runtime JARs for Java 8 compatible base classes.

Generate classpath with dependency:build-classpath -DincludeScope=runtime
-Dmdep.outputFile=target/runtime-classpath.txt. Multi-release alternatives and
module-info descriptors are ignored by a Java 8 VM and intentionally excluded.
"""
import json
import os
from pathlib import Path
import struct
import zipfile


def main():
    backend = Path(__file__).resolve().parents[1]
    classpath = (backend / 'target/runtime-classpath.txt').read_text(encoding='utf-8').strip()
    jars = [Path(item) for item in classpath.split(os.pathsep) if item]
    if not jars:
        raise RuntimeError('Runtime classpath is empty')
    checked = 0
    failures = []
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for name in archive.namelist():
                if not name.endswith('.class') or name.startswith('META-INF/versions/') or name.endswith('module-info.class'):
                    continue
                header = archive.read(name)[:8]
                if len(header) != 8 or header[:4] != b'\xca\xfe\xba\xbe':
                    raise RuntimeError('Invalid class file in ' + jar.name)
                major = struct.unpack('>H', header[6:8])[0]
                checked += 1
                if major > 52:
                    failures.append({'jar': jar.name, 'class': name, 'major': major})
    report = {'compatible': not failures, 'runtimeJars': len(jars), 'baseClasses': checked, 'failures': failures}
    (backend / 'target/java8-bytecode-check.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('Java 8 base bytecode check: jars=' + str(len(jars)) + ', classes=' + str(checked) + ', compatible=' + str(not failures))
    if failures:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
