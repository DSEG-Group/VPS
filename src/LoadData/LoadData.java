package LoadData;/*
 * LoadData - Load Sample Data directly into database tables or into
 * CSV files using multiple parallel workers.
 *
 * Copyright (C) 2016, Denis Lussier
 * Copyright (C) 2016, Jan Wieck
 *
 */

import client.jTPCCRandom;

import java.sql.*;
import java.util.*;
import java.io.*;
import java.lang.Integer;

public class LoadData
{
    private static Properties   ini = new Properties();
    private static String       db;
    private static Properties   dbProps;
    private static jTPCCRandom rnd;
    private static String       fileLocation = null;
    private static String       csvNullValue = null;
	private static String       resultDirectory = null;
	private static String       resultSQLName = null;
	private static String 		iDB = null;
	private static int 		    dbtype;


    private static int          numWarehouses;
    private static int          numWorkers;
    private static int          nextJob = 0;
    private static Object       nextJobLock = new Object();

    private static LoadDataWorker[] workers;
    private static Thread[]     workerThreads;

    private static String[]     argv;

    private static boolean              writeCSV = false;
    private static BufferedWriter       configCSV = null;
    private static BufferedWriter       itemCSV = null;
    private static BufferedWriter       warehouseCSV = null;
    private static BufferedWriter       districtCSV = null;
    private static BufferedWriter       stockCSV = null;
    private static BufferedWriter       customerCSV = null;
    private static BufferedWriter       historyCSV = null;
    private static BufferedWriter       orderCSV = null;
    private static BufferedWriter       orderLineCSV = null;
    private static BufferedWriter       newOrderCSV = null;
	private static BufferedWriter 		resultSQL = null;
	

	public final static int     DB_UNKNOWN = 0,
								DB_FIREBIRD = 1,
								DB_ORACLE = 2,
								DB_POSTGRES = 3,
								DB_MYSQL = 4,
								DB_COCKROACH = 5;

    public static void main(String[] args) {
	int     i;

	System.out.println("Starting BenchmarkSQL LoadData");
	System.out.println("");

	/*
	 * Load the Benchmark properties file.
	 */
	try
	{
	    ini.load(new FileInputStream(System.getProperty("prop")));
	}
	catch (IOException e)
	{
	    System.err.println("ERROR: " + e.getMessage());
	    System.exit(1);
	}
	argv = args;

	/*
	 * Initialize the global Random generator that picks the
	 * C values for the load.
	 */
	rnd = new jTPCCRandom();

	/*
	 * Load the JDBC driver and prepare the db and dbProps.
	 */
	try {
	    Class.forName(iniGetString("driver"));
	}
	catch (Exception e)
	{
	    System.err.println("ERROR: cannot load JDBC driver - " +
			       e.getMessage());
	    System.exit(1);
	}
	db = iniGetString("conn");
	// iDB = iniGetString("db");
	dbProps = new Properties();
	dbProps.setProperty("user", iniGetString("user"));
	dbProps.setProperty("password", iniGetString("password"));

	/*
	 * Parse other vital information from the props file.
	 */
	numWarehouses   = iniGetInt("warehouses");
	numWorkers      = iniGetInt("loadWorkers", 4);
	fileLocation    = iniGetString("fileLocation");
	csvNullValue    = iniGetString("csvNullValue", "NULL");


	
	// //写死文件地址，存储创表的SQL语句
	// resultDirectory = "/home/dseg/Desktop/lyb/my_benchmark/run/";
	// resultSQLName = resultDirectory + "CreateDatabase.sql";
	// File SQLFile = new File(resultSQLName);
	// try {
	// 	resultSQL = new BufferedWriter(new FileWriter(resultSQLName));
	// } catch (IOException e) {
	// 	System.exit(1);
	// }


	// if (iDB.equals("firebird")){
	// 	dbtype = DB_FIREBIRD;}
	// else if (iDB.equals("oracle")){
	// 	dbtype = DB_ORACLE;}
	// else if (iDB.equals("postgres")){
	// 	dbtype = DB_POSTGRES;}
	// else if (iDB.equals("mysql")){
	// 	dbtype = DB_MYSQL;}
	// else if (iDB.equals("cockroach")){
	// 	dbtype = DB_COCKROACH;}
	// else {
	// 	System.out.print("not such db\n");
	// 	return;
	// }




	// ExtremeHighWeight = iniGetInt("ExtremeHighWeight");
	// HighWeight = iniGetInt("HighWeight");
	// NormalWeight = iniGetInt("NormalWeight");
	// LowWeight = iniGetInt("LowWeight");


	/*
	 * If CSV files are requested, open them all.
	 */
	if (fileLocation != null)
	{
	    writeCSV = true;

	    try
	    {
		configCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_config.csv"));
		itemCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_item.csv"));
		warehouseCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_warehouse.csv"));
		districtCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_district.csv"));
		stockCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_stock.csv"));
		customerCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_customer.csv"));
		historyCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_history.csv"));
		orderCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_oorder.csv"));
		orderLineCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_order_line.csv"));
		newOrderCSV = new BufferedWriter(new FileWriter(fileLocation +
							      "bmsql_new_order.csv"));
	    }
	    catch (IOException ie)
	    {
		System.err.println(ie.getMessage());
		System.exit(3);
	    }
	}

	System.out.println("");

	/*
	 * Create the number of requested workers and start them.
	 */
	workers = new LoadDataWorker[numWorkers];
	workerThreads = new Thread[numWorkers];
	for (i = 0; i < numWorkers; i++)
	{
	    Connection dbConn;

	    try
	    {
		dbConn = DriverManager.getConnection(db, dbProps);
		dbConn.setAutoCommit(false);
		if (writeCSV)
		    workers[i] = new LoadDataWorker(i, csvNullValue,
							rnd.newRandom(),dbtype);
		else
		    workers[i] = new LoadDataWorker(i, dbConn,
							rnd.newRandom(),dbtype);
		workerThreads[i] = new Thread(workers[i]);
		workerThreads[i].start();
	    }
	    catch (SQLException se)
	    {
		System.err.println("ERROR: " + se.getMessage());
		System.exit(3);
		return;
	    }

	}

	for (i = 0; i < numWorkers; i++)
	{
	    try {
		workerThreads[i].join();
	    }
	    catch (InterruptedException ie)
	    {
		System.err.println("ERROR: worker " + i + " - " +
				   ie.getMessage());
		System.exit(4);
	    }
	}


	/*
	 * Close the CSV files if we are writing them.
	 */
	if (writeCSV)
	{
	    try
	    {
		configCSV.close();
		itemCSV.close();
		warehouseCSV.close();
		districtCSV.close();
		stockCSV.close();
		customerCSV.close();
		historyCSV.close();
		orderCSV.close();
		orderLineCSV.close();
		newOrderCSV.close();
	    }
	    catch (IOException ie)
	    {
		System.err.println(ie.getMessage());
		System.exit(3);
	    }
	}
	// if (resultSQL != null) {
	// 	try {
	// 		resultSQL.close();
	// 	} catch (IOException e) {};
	// }
    } // End of main()

	// public static void SQLAppend(String SQLString){
	//     synchronized(resultSQL){
	// 		try {
	// 			resultSQL.write(SQLString);
	// 		}
	// 		catch (Exception ie)
	// 		{
	// 		System.err.println("ERROR:  write"+
	// 				ie.getMessage());
	// 		System.exit(4);
	// 		}
	// 	}
	// }

	// public void writeSQLFile(String SQLString){
	// 	try{
	// 		resultSQL.write(SQLString);
	// 	}catch(IOException e){
	// 		System.err.println(e.getMessage());
	// 	}
	// }

    public static void configAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(configCSV)
	{
	    configCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void itemAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(itemCSV)
	{
	    itemCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void warehouseAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(warehouseCSV)
	{
	    warehouseCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void districtAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(districtCSV)
	{
	    districtCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void stockAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(stockCSV)
	{
	    stockCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void customerAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(customerCSV)
	{
	    customerCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void historyAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(historyCSV)
	{
	    historyCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void orderAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(orderCSV)
	{
	    orderCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void orderLineAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(orderLineCSV)
	{
	    orderLineCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static void newOrderAppend(StringBuffer buf)
	throws IOException
    {
	synchronized(newOrderCSV)
	{
	    newOrderCSV.write(buf.toString());
	}
	buf.setLength(0);
    }

    public static int getNextJob()
    {
	int     job;

	synchronized(nextJobLock)
	{
	    if (nextJob > numWarehouses)
		job = -1;
	    else
		job = nextJob++;
	}

	return job;
    }

    public static int getNumWarehouses()
    {
	return numWarehouses;
    }

    private static String iniGetString(String name)
    {
	String  strVal = null;

	for (int i = 0; i < argv.length - 1; i += 2)
	{
	    if (name.toLowerCase().equals(argv[i].toLowerCase()))
	    {
		strVal = argv[i + 1];
		break;
	    }
	}

	if (strVal == null)
	    strVal = ini.getProperty(name);

	if (strVal == null)
	    System.out.println(name + " (not defined)");
	else
	    if (name.equals("password"))
		System.out.println(name + "=***********");
	    else
		System.out.println(name + "=" + strVal);
	return strVal;
    }

    private static String iniGetString(String name, String defVal)
    {
	String  strVal = null;

	for (int i = 0; i < argv.length - 1; i += 2)
	{
	    if (name.toLowerCase().equals(argv[i].toLowerCase()))
	    {
		strVal = argv[i + 1];
		break;
	    }
	}

	if (strVal == null)
	    strVal = ini.getProperty(name);

	if (strVal == null)
	{
	    System.out.println(name + " (not defined - using default '" +
			       defVal + "')");
	    return defVal;
	}
	else
	    if (name.equals("password"))
		System.out.println(name + "=***********");
	    else
		System.out.println(name + "=" + strVal);
	return strVal;
    }

    private static int iniGetInt(String name)
    {
	String  strVal = iniGetString(name);

	if (strVal == null)
	    return 0;
	return Integer.parseInt(strVal);
    }

    private static int iniGetInt(String name, int defVal)
    {
	String  strVal = iniGetString(name);

	if (strVal == null)
	    return defVal;
	return Integer.parseInt(strVal);
    }
}
