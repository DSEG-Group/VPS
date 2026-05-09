#!/usr/bin/env bash

export JAVA_HOME=/home/dseg/Desktop/lyb/tpcc/JDK8
export PATH=$PATH:$JAVA_HOME/bin

FILES=(
    # "my_mysql.properties"
    # "my_oracle.properties"
    "props.pg"
)

for file in "${FILES[@]}"; do
    # for i in 100; do

        # sed -i "s#^terminals=.*#terminals=${i}#g" "$file"

        set -- "$file"

        if [ $# -ne 1 ] ; then
            echo "usage: $(basename $0) PROPS_FILE" >&2
            exit 2
        fi

        SEQ_FILE="./.jTPCC_run_seq.dat"
        if [ ! -f "${SEQ_FILE}" ] ; then
            echo "0" > "${SEQ_FILE}"
        fi
        SEQ=$(expr $(cat "${SEQ_FILE}") + 1) || exit 1
        echo "${SEQ}" > "${SEQ_FILE}"

        source funcs.sh $1

        setCP || exit 1

        myOPTS="-Dprop=$1 -DrunID=${SEQ}"

        java -cp "$myCP" $myOPTS client.jTPCC
    # done

done
