#!/bin/bash

CONTAINER=oracle19c_ee

docker exec -i $CONTAINER /bin/bash << 'EOF'

rman target << 'EOSQL'

DELETE NOPROMPT ARCHIVELOG FROM TIME "TO_DATE('2025-08-10', 'YYYY-MM-DD')";

exit;
EOSQL
EOF
