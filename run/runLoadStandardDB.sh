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
    psql -h 0.0.0.0 -p 11452 -U postgres -d bmsql -f ./bmsql.sql
fi
if [ "$DB" = "mysql" ] ; then
    mysql -h 0.0.0.0 -P 4397 -u root -p123456 -e "create database bmsql;" 
    mysql -h 0.0.0.0 -P 4397 -u root -p123456 --max_allowed_packet=1G --net_buffer_length=1M --local-infile=1 bmsql < ./standard_data/standardDatabase_mysql_distribution_realic.sql
fi
if [ "$DB" = "oracle" ] ; then
    sqlplus lyb/123456@172.17.0.4:1521/ORCLPDB @/home/lyb/vps_benchmark/run/standard_data/standardDatabase_oracle.sql
fi