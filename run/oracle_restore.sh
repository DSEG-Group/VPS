#!/bin/bash

CONTAINER=oracle19c_ee

docker exec -i $CONTAINER /bin/bash << 'EOF'

sqlplus / as sysdba << 'EOSQL'

shutdown immediate;
startup mount;

flashback database to restore point standard_database;

alter database open resetlogs;

exit;
EOSQL
EOF
