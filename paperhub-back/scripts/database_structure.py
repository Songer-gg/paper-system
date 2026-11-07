"""Read-only, deterministic MySQL schema inventory; no row contents or credentials."""

import json

STRUCTURE_SQL = """
SELECT CONCAT('table:',TABLE_NAME), JSON_OBJECT('engine',ENGINE,'collation',TABLE_COLLATION)
FROM information_schema.TABLES WHERE TABLE_SCHEMA='paperhub' ORDER BY TABLE_NAME;
SELECT CONCAT('column:',TABLE_NAME,':',COLUMN_NAME),
JSON_OBJECT('type',COLUMN_TYPE,'nullable',IS_NULLABLE,'default',COLUMN_DEFAULT,
'extra',EXTRA,'generated',GENERATION_EXPRESSION,'collation',COLLATION_NAME)
FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='paperhub' ORDER BY TABLE_NAME,ORDINAL_POSITION;
SELECT CONCAT('index:',TABLE_NAME,':',INDEX_NAME,':',SEQ_IN_INDEX),
JSON_OBJECT('column',COLUMN_NAME,'unique',1-NON_UNIQUE,'type',INDEX_TYPE,'prefix',SUB_PART,'direction',COLLATION)
FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='paperhub' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;
SELECT CONCAT('foreign:',k.TABLE_NAME,':',k.CONSTRAINT_NAME,':',k.ORDINAL_POSITION),
JSON_OBJECT('column',k.COLUMN_NAME,'table',k.REFERENCED_TABLE_NAME,'reference',k.REFERENCED_COLUMN_NAME,
'delete',r.DELETE_RULE,'update',r.UPDATE_RULE)
FROM information_schema.KEY_COLUMN_USAGE k JOIN information_schema.REFERENTIAL_CONSTRAINTS r
ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME
WHERE k.CONSTRAINT_SCHEMA='paperhub' AND k.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY k.TABLE_NAME,k.CONSTRAINT_NAME,k.ORDINAL_POSITION;
SELECT CONCAT('check:',CONSTRAINT_NAME),JSON_OBJECT('clause',CHECK_CLAUSE)
FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA='paperhub' ORDER BY CONSTRAINT_NAME;
"""

def decode_inventory(raw):
    result = {}
    for line in raw.splitlines():
        key, value = line.split('\t', 1)
        result[key] = json.loads(value)
    return result

