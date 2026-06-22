# VPS: Rethinking OLTP Database Performance Evaluation through Transactional Value

This project contains the prototype of the VPS Benchmark descibed in
```
VPS: Rethinking OLTP Database Performance Evaluation through Transactional Value
Jianbin Qin, Yibin Lin, Wendi Hua, Rui Mao, Chuan Xiao
ICDE 2026
https://szudseg.cn/?list_4/195.html
```

## Requirements

Before running this project, please ensure that the following software is installed:

- JDK 8 (Java 1.8)
- Apache Ant

Check the installed Java version:

```bash
java -version
```

Expected output:

```text
java version "1.8.0_xxx"
```

## Running
### Create the tables
```bash
./runDatabaseBuild.sh props.pg
```

### Run the benchmark
```bash
./runBenchmark.sh props.pg
```

### Drop the tables
```bash
./runDatabaseDestroy.sh props.pg
```

### Load the tables
```bash
./runLoadStandardDB.sh props.pg
```