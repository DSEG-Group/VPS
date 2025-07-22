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

if [ "$DB" = "postgres" ] ; then
    createdb -h 0.0.0.0 -p 11452 -U postgres -W bmsql
    psql -h 0.0.0.0 -p 11452 -U postgres -d bmsql -f ./standard_data/standardDatabase_pg.sql
fi
if [ "$DB" = "mysql" ] ; then
    # mysql -h 0.0.0.0 -P 4397 -u root -p123456 -e "create database bmsql;" 
    mysql -h 0.0.0.0 -P 4397 -u root -p123456 bmsql < ./standard_data/standardDatabase_mysql.sql
fi
# if ["$DB" = "oracle"] ; then
#     sqlplus root/123456@172.17.0.7:1521/ORCLPDB @/home/lyb/vps_benchmark/run/standard_data/standardDatabase_oracle.sql
# fi