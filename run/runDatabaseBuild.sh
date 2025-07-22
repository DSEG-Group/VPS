#!/bin/sh

if [ $# -lt 1 ] ; then
    echo "usage: $(basename $0) PROPS [OPT VAL [...]]" >&2
    exit 2
fi

PROPS="$1"
shift
if [ ! -f "${PROPS}" ] ; then
    echo "${PROPS}: no such file or directory" >&2
    exit 1
fi
DB="$(grep '^db=' $PROPS | sed -e 's/^db=//')"

BEFORE_LOAD="tableCreates"

AFTER_LOAD="indexCreates foreignKeys extraHistID buildFinish"

for step in ${BEFORE_LOAD} ; do
    ./runSQL.sh "${PROPS}" $step
done

./runLoader.sh "${PROPS}" $*

# ./runSQL.sh "${PROPS}" ./standard_data/LoadData.sql

for step in ${AFTER_LOAD} ; do
    ./runSQL.sh "${PROPS}" $step
done

# 导出整个数据库
# if [ "$DB" = "postgres" ] ; then
#     pg_dump postgresql://lyb:123456@localhost:5432/postgres -F p > ./standard_data/standardDatabase_pg.sql
# fi
# if [ "$DB" = "mysql" ] ; then
#     mysqldump -u root -p123456 tpcc > ./standard_data/standardDatabase_mysql.sql
# fi