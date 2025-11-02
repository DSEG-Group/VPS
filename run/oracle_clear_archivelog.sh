#!/bin/bash

CONTAINER=oracle19c_ee

docker exec -i $CONTAINER /bin/bash << 'EOF'

sqlplus / as sysdba << 'EOSQL'

shutdown abort;
startup mount;
exit;
EOSQL

rman target / << 'EOSQL'

DELETE NOPROMPT ARCHIVELOG FROM TIME "TO_DATE('2025-09-17 11:07:00', 'YYYY-MM-DD HH24:MI:SS')";

exit;
EOSQL

sqlplus / as sysdba << 'EOSQL'
SHUTDOWN IMMEDIATE;
STARTUP MOUNT;
flashback database to restore point standard_database;
alter database open resetlogs;
exit;
EOSQL

EOF
