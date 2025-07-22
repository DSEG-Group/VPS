package client;/*
				* jTPCC - Open Source Java implementation of a TPC-C like benchmark
				*
				* Copyright (C) 2003, Raul Barbosa
				* Copyright (C) 2004-2016, Denis Lussier
				* Copyright (C) 2016, Jan Wieck
				*
				*/

import OSCollector.OSCollector;
import org.apache.log4j.*;
import org.firebirdsql.jdbc.parser.JaybirdSqlParser.substringFunction_return;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.text.*;
import java.util.stream.Stream;




public class jTPCC implements jTPCCConfig {
	private static org.apache.log4j.Logger log = Logger.getLogger(jTPCC.class);
	private static String resultDirName = null;
	private static BufferedWriter resultCSV = null;
	private static BufferedWriter runInfoCSV = null;
	private static BufferedWriter resultSQL = null;
	private static int runID = 0;

	private int dbType = DB_UNKNOWN;
	private int currentlyDisplayedTerminal;
	private boolean isHeap = false;

	private jTPCCTerminal[] terminals;
	private String[] terminalNames;
	private boolean terminalsBlockingExit = false;
	private long terminalsStarted = 0, sessionCount = 0, transactionCount = 0, transValCount = 0;// change 11.13
	private long abortCount = 0;
	private Object counterLock = new Object();

	private long newOrderCounter = 0, sessionStartTimestamp, sessionEndTimestamp, sessionNextTimestamp = 0,nextChangeTime = 0,
			sessionNextKounter = 0;
	private long epochStartTime, epochEndTime;
	private long sessionEndTargetTime = -1, fastNewOrderCounter, recentTpmC = 0, recentTpmTotal = 0;
	private boolean signalTerminalsRequestEndSent = false, databaseDriverLoaded = false;

	private FileOutputStream fileOutputStream;
	private PrintStream printStreamReport;
	private String sessionStart, sessionEnd;
	private int limPerMin_Terminal;

	private double tpmC;
	private jTPCCRandom rnd;
	private OSCollector osCollector = null;
	private HashMap<String, Long> costPerWorkerload;

	private int numTerminals = -1;
	private Vector<Long>latency_queue = new Vector<>();
	private Vector<Long>E_H_latency_q = new Vector<>();
	private Vector<Long>H_latency_q = new Vector<>();
	private Vector<Long>N_latency_q = new Vector<>();
	private Vector<Long>L_latency_q = new Vector<>();

	private boolean limitIsTime;
				
	private int transactionsPerTerminal = -1;
	private int numWarehouses = -1;
	private int loadWarehouses = -1;
	private int[] newOrderWeightValue = {-1,-1}, paymentWeightValue = {-1,-1}, orderStatusWeightValue  = {-1,-1},
			deliveryWeightValue  = {-1,-1}, stockLevelWeightValue  = {-1,-1};
	private long changeTime = 0;//运行若干时间后workload的各种事务权重发生变化。
	private long executionTimeMillis = -1;

	private String sqlDataJsonPath = "./standard_data/result.json";
	private BufferedReader SQLFileReader = null;

	private boolean standardSQL = false;
	public Heap Tree;
	private int limitValue;

	private int total_txn;

	private long lastEpochValue = 0;
	private long lastEpochTransCount = 0;
	private boolean value_loss = false;

	private Vector<Double> epochVpmTotalRecords = new Vector<>();
	private Vector<Double> epochTpmTotalRecords = new Vector<>();
	
	private LRUCache<Integer,Double> hot_item = new LRUCache<>(20);


	static {
        // 每次运行时生成唯一时间戳
        System.setProperty("currentTimestamp", String.valueOf(System.currentTimeMillis()));
    }

	public static void main(String args[]) {
		PropertyConfigurator.configure("log4j.properties");
		new jTPCC();
	}

	private String getProp(Properties p, String pName) {
		String prop = p.getProperty(pName);
		costPerWorkerload = new HashMap<String, Long>();
		log.info("Term-00, " + pName + "=" + prop);
		return (prop);
	}

	public jTPCC() {

		// load the ini file
		Properties ini = new Properties();
		try {
			ini.load(new FileInputStream(System.getProperty("prop")));
		} catch (IOException e) {
			errorMessage("Term-00, could not load properties file");
		}

		

		log.info("Term-00, ");
		log.info("Term-00, +-------------------------------------------------------------+");
		log.info("Term-00,      BenchmarkSQL v" + JTPCCVERSION);
		log.info("Term-00, +-------------------------------------------------------------+");
		log.info("Term-00,  (c) 2003, Raul Barbosa");
		log.info("Term-00,  (c) 2004-2016, Denis Lussier");
		log.info("Term-00,  (c) 2016, Jan Wieck");
		log.info("Term-00, +-------------------------------------------------------------+");
		log.info("Term-00, ");
		String iDB = getProp(ini, "db");
		String iDriver = getProp(ini, "driver");
		String iConn = getProp(ini, "conn");
		String iUser = getProp(ini, "user");
		String iPassword = ini.getProperty("password");

		log.info("Term-00, ");
		String iWarehouses = getProp(ini, "warehouses");
		String iTerminals = getProp(ini, "terminals");

		String iRunTxnsPerTerminal = ini.getProperty("runTxnsPerTerminal");
		String iRunMins = ini.getProperty("runMins");
		Tree = new Heap();
		if (Integer.parseInt(iRunTxnsPerTerminal) == 0 && Integer.parseInt(iRunMins) != 0) {
			log.info("Term-00, runMins" + "=" + iRunMins);
		} else if (Integer.parseInt(iRunTxnsPerTerminal) != 0 && Integer.parseInt(iRunMins) == 0) {
			log.info("Term-00, runTxnsPerTerminal" + "=" + iRunTxnsPerTerminal);
		} else {
			errorMessage("Term-00, Must indicate either transactions per terminal or number of run minutes!");
		}
		;
		String limPerMin = getProp(ini, "limitTxnsPerMin");
		String iTermWhseFixed = getProp(ini, "terminalWarehouseFixed");
		log.info("Term-00, ");
		String iNewOrderWeight_1 = getProp(ini, "newOrderWeight_1");
		String iPaymentWeight_1 = getProp(ini, "paymentWeight_1");
		String iOrderStatusWeight_1 = getProp(ini, "orderStatusWeight_1");
		String iDeliveryWeight_1 = getProp(ini, "deliveryWeight_1");
		String iStockLevelWeight_1 = getProp(ini, "stockLevelWeight_1");

		String iNewOrderWeight_2 = getProp(ini, "newOrderWeight_2");
		String iPaymentWeight_2 = getProp(ini, "paymentWeight_2");
		String iOrderStatusWeight_2 = getProp(ini, "orderStatusWeight_2");
		String iDeliveryWeight_2 = getProp(ini, "deliveryWeight_2");
		String iStockLevelWeight_2 = getProp(ini, "stockLevelWeight_2");

		String iChangeTime = getProp(ini, "changTime_second");

		String iStandardSQL = getProp(ini,"standardSQL");
		String iIsHeap = getProp(ini,"heapSQL");
		String iTotal_txn = getProp(ini, "total_txn");
		String iLimitValue = getProp(ini, "limitValue");
		String iValueLoss = getProp(ini,"value_loss");



		log.info("Term-00, ");
		String resultDirectory = getProp(ini, "resultDirectory");
		String osCollectorScript = getProp(ini, "osCollectorScript");
		
		if(iDB.equals("postgres")){
			sqlDataJsonPath = "./standard_data/pg_result.json";
		}
		else if(iDB.equals("mysql")){
			sqlDataJsonPath = "./standard_data/mysql_result.json";
		}

		readJson();

		log.info("Term-00, ");

		if (iDB.equals("firebird"))
			dbType = DB_FIREBIRD;
		else if (iDB.equals("oracle"))
			dbType = DB_ORACLE;
		else if (iDB.equals("postgres"))
			dbType = DB_POSTGRES;
		else if (iDB.equals("mysql"))
			dbType = DB_MYSQL;
		else if (iDB.equals("cockroach"))
			dbType = DB_COCKROACH;
		else {
			log.error("unknown database type '" + iDB + "'");
			return;
		}

		if (iValueLoss.equals("1")){
			value_loss = true;
		} 
		else{
			value_loss = false;
		}

		if (Integer.parseInt(limPerMin) != 0) {
			limPerMin_Terminal = Integer.parseInt(limPerMin) / Integer.parseInt(iTerminals);
		} else {
			limPerMin_Terminal = -1;
		}

		boolean iRunMinsBool = false;

		changeTime = Long.parseLong(iChangeTime);

		try {
			String driver = iDriver;
			printMessage("Loading database driver: \'" + driver + "\'...");
			Class.forName(iDriver);
			databaseDriverLoaded = true;
		} catch (Exception ex) {
			errorMessage("Unable to load the database driver!");
			databaseDriverLoaded = false;
		}

		if (databaseDriverLoaded && resultDirectory != null) {
			StringBuffer sb = new StringBuffer();
			Formatter fmt = new Formatter(sb);
			Pattern p = Pattern.compile("%t");
			Calendar cal = Calendar.getInstance();

			String iRunID;

			iRunID = System.getProperty("runID");
			if (iRunID != null) {
				runID = Integer.parseInt(iRunID);
			}

			/*
			 * Split the resultDirectory into strings around
			 * patterns of %t and then insert date/time formatting
			 * based on the current time. That way the resultDirectory
			 * in the properties file can have date/time format
			 * elements like in result_%tY-%tm-%td to embed the current
			 * date in the directory name.
			 */
			String[] parts = p.split(resultDirectory, -1);
			sb.append(parts[0]);
			for (int i = 1; i < parts.length; i++) {
				fmt.format("%t" + parts[i].substring(0, 1), cal);
				sb.append(parts[i].substring(1));
			}
			resultDirName = sb.toString();
			File resultDir = new File(resultDirName);
			File resultDataDir = new File(resultDir, "data");

			// Create the output directory structure.
			if (!resultDir.mkdir()) {
				log.error("Failed to create directory '" +
						resultDir.getPath() + "'");
				System.exit(1);
			}
			if (!resultDataDir.mkdir()) {
				log.error("Failed to create directory '" +
						resultDataDir.getPath() + "'");
				System.exit(1);
			}

			// Copy the used properties file into the resultDirectory.
			try {
				Files.copy(new File(System.getProperty("prop")).toPath(),
						new File(resultDir, "run.properties").toPath());
			} catch (IOException e) {
				log.error(e.getMessage());
				System.exit(1);
			}
			log.info("Term-00, copied " + System.getProperty("prop") +
					" to " + new File(resultDir, "run.properties").toPath());

			// Create the runInfo.csv file.
			String runInfoCSVName = new File(resultDataDir, "runInfo.csv").getPath();
			try {
				runInfoCSV = new BufferedWriter(
						new FileWriter(runInfoCSVName));
				runInfoCSV.write("run,driver,driverVersion,db,sessionStart," +
						"runMins," +
						"loadWarehouses,runWarehouses,numSUTThreads," +
						"limitTxnsPerMin," +
						"thinkTimeMultiplier,keyingTimeMultiplier\n");
			} catch (IOException e) {
				log.error(e.getMessage());
				System.exit(1);
			}
			log.info("Term-00, created " + runInfoCSVName + " for runID " +
					runID);

			// Open the per transaction result.csv file.
			String resultCSVName = new File(resultDataDir, "result.csv").getPath();
			try {
				resultCSV = new BufferedWriter(new FileWriter(resultCSVName));
				resultCSV.write("run,transGenerateTime,transStart,elapsed,latency,dblatency," +
						"ttype,rbk,dskipped,Value_real,abort,Prioity,error\n");
			} catch (IOException e) {
				log.error(e.getMessage());
				System.exit(1);
			}
			log.info("Term-00, writing per transaction results to " +
					resultCSVName);

			// Open the per transaction result.sql file
			String resultSQLName = new File(resultDataDir, "result.json").getPath();
			try {
				resultSQL = new BufferedWriter(new FileWriter(resultSQLName));
			} catch (IOException e) {
				log.error(e.getMessage());
				System.exit(1);
			}

			if (osCollectorScript != null) {
				osCollector = new OSCollector(getProp(ini, "osCollectorScript"),
						runID,
						Integer.parseInt(getProp(ini, "osCollectorInterval")),
						getProp(ini, "osCollectorSSHAddr"),
						getProp(ini, "osCollectorDevices"),
						resultDataDir, log);
			}

			log.info("Term-00,");
		}

		if (databaseDriverLoaded) {
			try {

				boolean terminalWarehouseFixed = true;
				long CLoad;

				Properties dbProps = new Properties();
				dbProps.setProperty("user", iUser);
				dbProps.setProperty("password", iPassword);

				/*
				 * Fine tuning of database conneciton parameters if needed.
				 */
				switch (dbType) {
					case DB_FIREBIRD:
						/*
						 * Firebird needs no_rec_version for our load
						 * to work. Even with that some "deadlocks"
						 * occur. Note that the message "deadlock" in
						 * Firebird can mean something completely different,
						 * namely that there was a conflicting write to
						 * a row that could not be resolved.
						 */
						dbProps.setProperty("TRANSACTION_READ_COMMITTED",
								"isc_tpb_read_committed," +
										"isc_tpb_no_rec_version," +
										"isc_tpb_write," +
										"isc_tpb_wait");
						break;

					default:
						break;
				}

				try {
					loadWarehouses = Integer.parseInt(jTPCCUtil.getConfig(iConn,
							dbProps, "warehouses"));
					CLoad = Long.parseLong(jTPCCUtil.getConfig(iConn,
							dbProps, "nURandCLast"));
				} catch (Exception e) {
					errorMessage(e.getMessage());
					throw e;
				}
				this.rnd = new jTPCCRandom(CLoad);
				log.info("Term-00, C value for C_LAST during load: " + CLoad);
				log.info("Term-00, C value for C_LAST this run:    " + rnd.getNURandCLast());
				log.info("Term-00, ");

				fastNewOrderCounter = 0;
				updateStatusLine();

				try {
					if (Integer.parseInt(iRunMins) != 0 && Integer.parseInt(iRunTxnsPerTerminal) == 0) {
						iRunMinsBool = true;
					} else if (Integer.parseInt(iRunMins) == 0 && Integer.parseInt(iRunTxnsPerTerminal) != 0) {
						iRunMinsBool = false;
					} else {
						throw new NumberFormatException();
					}
				} catch (NumberFormatException e1) {
					errorMessage("Must indicate either transactions per terminal or number of run minutes!");
					throw new Exception();
				}

				try {
					numWarehouses = Integer.parseInt(iWarehouses);
					if (numWarehouses <= 0)
						throw new NumberFormatException();
				} catch (NumberFormatException e1) {
					errorMessage("Invalid number of warehouses!");
					throw new Exception();
				}
				if (numWarehouses > loadWarehouses) {
					errorMessage("numWarehouses cannot be greater " +
							"than the warehouses loaded in the database");
					throw new Exception();
				}

				try {
					numTerminals = Integer.parseInt(iTerminals);
					// if(numTerminals <= 0 || numTerminals > 10*numWarehouses)
					if(numTerminals <= 0)
					throw new NumberFormatException();
				} catch (NumberFormatException e1) {
					errorMessage("Invalid number of terminals!");
					throw new Exception();
				}

				if (Double.parseDouble(iRunMins) != 0 && Integer.parseInt(iRunTxnsPerTerminal) == 0) {
					try {
						executionTimeMillis = (long)(Double.parseDouble(iRunMins) * 1000);
						if (executionTimeMillis <= 0)
							throw new NumberFormatException();
					} catch (NumberFormatException e1) {
						errorMessage("Invalid number of minutes!");
						throw new Exception();
					}
				} else {
					try {
						transactionsPerTerminal = Integer.parseInt(iRunTxnsPerTerminal);
						if (transactionsPerTerminal <= 0)
							throw new NumberFormatException();
					} catch (NumberFormatException e1) {
						errorMessage("Invalid number of transactions per terminal!");
						throw new Exception();
					}
				}

				terminalWarehouseFixed = Boolean.parseBoolean(iTermWhseFixed);
				int IntStandardSQL = Integer.parseInt(iStandardSQL);
				int IntIsHeap = Integer.parseInt(iIsHeap);

				try {
					newOrderWeightValue[0] = Integer.parseInt(iNewOrderWeight_1);
					paymentWeightValue[0] = Integer.parseInt(iPaymentWeight_1);
					orderStatusWeightValue[0] = Integer.parseInt(iOrderStatusWeight_1);
					deliveryWeightValue[0] = Integer.parseInt(iDeliveryWeight_1);
					stockLevelWeightValue[0] = Integer.parseInt(iStockLevelWeight_1);

					if(changeTime>=0){
						newOrderWeightValue[1] = Integer.parseInt(iNewOrderWeight_2);
						paymentWeightValue[1] = Integer.parseInt(iPaymentWeight_2);
						orderStatusWeightValue[1] = Integer.parseInt(iOrderStatusWeight_2);
						deliveryWeightValue[1] = Integer.parseInt(iDeliveryWeight_2);
						stockLevelWeightValue[1] = Integer.parseInt(iStockLevelWeight_2);
					}

					
					
					total_txn = Integer.parseInt(iTotal_txn);
					limitValue = Integer.parseInt(iLimitValue);


					if(IntIsHeap == 1&& IntStandardSQL == 1){
						throw new NumberFormatException();
					}
					if(IntStandardSQL == 1){
						standardSQL = true;
						isHeap = false;
					}
					if(IntIsHeap == 1){
						standardSQL = false;
						isHeap = true;
					}

					if (newOrderWeightValue[0] < 0 || paymentWeightValue[0] < 0 || orderStatusWeightValue[0] < 0
							|| deliveryWeightValue[0] < 0 || stockLevelWeightValue[0] < 0)
						throw new NumberFormatException();
					else if (newOrderWeightValue[0] == 0 && paymentWeightValue[0] == 0 && orderStatusWeightValue[0] == 0
							&& deliveryWeightValue[0] == 0 && stockLevelWeightValue[0] == 0)
						throw new NumberFormatException();

					if(changeTime>0){
						if (newOrderWeightValue[1] < 0 || paymentWeightValue[1] < 0 || orderStatusWeightValue[1] < 0
							|| deliveryWeightValue[1] < 0 || stockLevelWeightValue[1] < 0)
						throw new NumberFormatException();
						else if (newOrderWeightValue[1] == 0 && paymentWeightValue[1] == 0 && orderStatusWeightValue[1] == 0
							&& deliveryWeightValue[1] == 0 && stockLevelWeightValue[1] == 0)
						throw new NumberFormatException();
					}

				} catch (NumberFormatException e1) {
					if(IntIsHeap == 1&& IntStandardSQL == 1){
						errorMessage("heapSQL and standardSQL can't be 1 at the same time!");
						throw new Exception();
					}
					errorMessage("Invalid number in mix percentage!");
					throw new Exception();
				}

				if (newOrderWeightValue[0] + paymentWeightValue[0] + orderStatusWeightValue[0] + deliveryWeightValue[0]
						+ stockLevelWeightValue[0] > 100) {
					errorMessage("Sum of mix percentage parameters exceeds 100%!");
					throw new Exception();
				}

				if(changeTime>0){
					if (newOrderWeightValue[1] + paymentWeightValue[1] + orderStatusWeightValue[1] + deliveryWeightValue[1]
						+ stockLevelWeightValue[1] > 100) {
					errorMessage("Sum of mix percentage parameters exceeds 100%!");
					throw new Exception();
					}
				}

				newOrderCounter = 0;
				printMessage("Session started!");
				if (!limitIsTime)
					printMessage("Creating " + numTerminals + " terminal(s) with " + transactionsPerTerminal
							+ " transaction(s) per terminal...");
				else
					printMessage("Creating " + numTerminals + " terminal(s) with " + (executionTimeMillis / 60000)
							+ " minute(s) of execution...");
				if (terminalWarehouseFixed)
					printMessage("Terminal Warehouse is fixed");
				else
					printMessage("Terminal Warehouse is NOT fixed");
				printMessage("Transaction Weights version 1: " + newOrderWeightValue[0] + "% New-Order, " + paymentWeightValue[0]
						+ "% Payment, " + orderStatusWeightValue[0] + "% Order-Status, " + deliveryWeightValue[0]
						+ "% Delivery, " + stockLevelWeightValue[0] + "% Stock-Level");
				
				printMessage("Transaction Weights version 2: " + newOrderWeightValue[1] + "% New-Order, " + paymentWeightValue[1]
						+ "% Payment, " + orderStatusWeightValue[1] + "% Order-Status, " + deliveryWeightValue[1]
						+ "% Delivery, " + stockLevelWeightValue[1] + "% Stock-Level");

				printMessage("Number of Terminals\t" + numTerminals);
				if(isHeap){
					terminals = new jTPCCTerminal[numTerminals+1];
					terminalNames = new String[numTerminals+1];
				}
				else{
					terminals = new jTPCCTerminal[numTerminals];
					terminalNames = new String[numTerminals];
				}
				terminalsStarted = numTerminals;
				try {
					String database = iConn;
					String username = iUser;
					String password = iPassword;

					int[][] usedTerminals = new int[numWarehouses][10];
					for (int i = 0; i < numWarehouses; i++)
						for (int j = 0; j < 10; j++)
							usedTerminals[i][j] = 0;

					for (int i = 0; i <= numTerminals; i++) {
						int terminalWarehouseID;
						int terminalDistrictID;
						// do {
						terminalWarehouseID = rnd.nextInt(1, numWarehouses);
						terminalDistrictID = rnd.nextInt(1, 10);
						// } while (usedTerminals[terminalWarehouseID - 1][terminalDistrictID - 1] == 1);
						// usedTerminals[terminalWarehouseID - 1][terminalDistrictID - 1] = 1;

						String terminalName = "Term-" + (i >= 9 ? "" + (i + 1) : "0" + (i + 1));
						Connection conn = null;
						printMessage("Creating database connection for " + terminalName + "...");
						conn = DriverManager.getConnection(database, dbProps);
						conn.setAutoCommit(false);
						if(i < numTerminals){
							jTPCCTerminal terminal = new jTPCCTerminal(terminalName, terminalWarehouseID,
									terminalDistrictID,
									conn, dbType,
									transactionsPerTerminal, terminalWarehouseFixed,
									paymentWeightValue, orderStatusWeightValue,
									deliveryWeightValue, stockLevelWeightValue, numWarehouses, limPerMin_Terminal, this,standardSQL,isHeap,false,changeTime,value_loss);

							terminals[i] = terminal;
							terminalNames[i] = terminalName;
							printMessage(terminalName + "\t" + terminalWarehouseID);
						}
						else{
							if(isHeap){
								jTPCCTerminal terminal = new jTPCCTerminal(terminalName, terminalWarehouseID,
										terminalDistrictID,
										conn, dbType,
										transactionsPerTerminal, terminalWarehouseFixed,
										paymentWeightValue, orderStatusWeightValue,
										deliveryWeightValue, stockLevelWeightValue, numWarehouses, limPerMin_Terminal, this,standardSQL,false,true,changeTime,value_loss);

								terminals[i] = terminal;
								terminalNames[i] = terminalName;
								printMessage(terminalName + "\t" + terminalWarehouseID);
								terminalsStarted++;
							}
						}
					}

					sessionEndTargetTime = executionTimeMillis;
					signalTerminalsRequestEndSent = false;

					printMessage("Transaction\tWeight");
					printMessage("% New-Order\t" + newOrderWeightValue[0]);
					printMessage("% Payment\t" + paymentWeightValue[0]);
					printMessage("% Order-Status\t" + orderStatusWeightValue[0]);
					printMessage("% Delivery\t" + deliveryWeightValue[0]);
					printMessage("% Stock-Level\t" + stockLevelWeightValue[0]);

					printMessage("Transaction Number\tTerminal\tType\tExecution Time (ms)\t\tComment");

					printMessage("Created " + numTerminals + " terminal(s) successfully!");
					boolean dummvar = true;

					// Create Terminals, Start Transactions
					sessionStart = getCurrentTime();
					sessionStartTimestamp = System.currentTimeMillis();
					nextChangeTime = sessionStartTimestamp;
					epochStartTime = sessionStartTimestamp;
					sessionNextTimestamp = sessionStartTimestamp;
					if (sessionEndTargetTime != -1)
						sessionEndTargetTime += sessionStartTimestamp;

					// Record run parameters in runInfo.csv
					if (runInfoCSV != null) {
						try {
							StringBuffer infoSB = new StringBuffer();
							Formatter infoFmt = new Formatter(infoSB);
							infoFmt.format("%d,simple,%s,%s,%s,%s,%d,%d,%d,%d,1.0,1.0\n",
									runID, JTPCCVERSION, iDB,
									new Timestamp(sessionStartTimestamp).toString(),
									iRunMins,
									loadWarehouses,
									numWarehouses,
									numTerminals,
									Integer.parseInt(limPerMin));
							runInfoCSV.write(infoSB.toString());
							runInfoCSV.close();
						} catch (Exception e) {
							log.error(e.getMessage());
							System.exit(1);
						}
					}


					//启动多线程
					synchronized (terminals) {
						printMessage("Starting all terminals...");
						transactionCount = 1;
						for (int i = 0; i < terminals.length; i++)
							(new Thread(terminals[i])).start();
					}

					printMessage("All terminals started executing " + sessionStart);
				}

				catch (Exception e1) {
					errorMessage("This session ended with errors!");
					e1.printStackTrace();
					printStreamReport.close();
					fileOutputStream.close();

					throw new Exception();
				}

			} catch (Exception ex) {
			}
		}
		updateStatusLine();
	}

	private void signalTerminalsRequestEnd(boolean timeTriggered) {
		synchronized (terminals) {
			if (!signalTerminalsRequestEndSent) {
				if (timeTriggered)
					printMessage("The time limit has been reached.");
				printMessage("Signalling all terminals to stop...");
				signalTerminalsRequestEndSent = true;

				for (int i = 0; i < terminals.length; i++)
					if (terminals[i] != null){
						terminals[i].stopRunningWhenPossible();
					}
				printMessage("Waiting for all active transactions to end...");
			}
		}
	}

	public void signalTerminalEnded(jTPCCTerminal terminal, long countNewOrdersExecuted,Vector<Long>t_latency_queue, Vector<Long>E_H_latency_q,Vector<Long>H_latency_q,Vector<Long>N_latency_q,Vector<Long>L_latency_q) {
		synchronized (terminals) {
			boolean found = false;
			terminalsStarted--;
			for (int i = 0; i < terminals.length && !found; i++) {
				if (terminals[i] == terminal) {
					terminals[i] = null;
					terminalNames[i] = "(" + terminalNames[i] + ")";
					newOrderCounter += countNewOrdersExecuted;
					found = true;
				}
			}
			this.latency_queue.addAll(t_latency_queue);
			this.E_H_latency_q.addAll(E_H_latency_q);
			this.H_latency_q.addAll(H_latency_q);
			this.N_latency_q.addAll(N_latency_q);
			this.L_latency_q.addAll(L_latency_q);
		}

		if (terminalsStarted == 0) {
			sessionEnd = getCurrentTime();
			sessionEndTimestamp = System.currentTimeMillis();
			sessionEndTargetTime = -1;
			printMessage("All terminals finished executing " + sessionEnd);
			endReport();
			terminalsBlockingExit = false;
			printMessage("Session finished!");

			// If we opened a per transaction result file, close it.
			if (resultCSV != null) {
				try {
					resultCSV.close();
				} catch (IOException e) {
					log.error(e.getMessage());
				}
				;
			}

			if (resultSQL != null) {
				try {
					resultSQL.close();
				} catch (IOException e) {
					log.error(e.getMessage());
				}
				;
			}

			// Stop the OSCollector, if it is active.
			if (osCollector != null) {
				osCollector.stop();
				osCollector = null;
			}
		}
	}

	public void signalTerminalEndedTransaction(String terminalName, String transactionType, long executionTime,
			String comment, int newOrder, double transVal, int is_abort) {
		synchronized (counterLock) {
			transactionCount++;
			transValCount += transVal;// change 11.13
			fastNewOrderCounter += newOrder;
			Long counter = costPerWorkerload.get(transactionType);
			if (counter == null) {
				costPerWorkerload.put(transactionType, Long.valueOf(executionTime));
			} else {
				costPerWorkerload.put(transactionType, counter + executionTime);
			}
			if(is_abort == 1){
				abortCount++;
			}
		}
		if(total_txn !=0 && total_txn<=transactionCount){
			signalTerminalsRequestEnd(true);
		}
		if(limitValue != 0 && limitValue <= transValCount){
			signalTerminalsRequestEnd(true);
		}

		if (sessionEndTargetTime != -1 && System.currentTimeMillis() > sessionEndTargetTime) {
			signalTerminalsRequestEnd(true);
		}

		updateStatusLine();

	}

	public jTPCCRandom getRnd() {
		return rnd;
	}

	public void resultAppend(jTPCCTData term,boolean isStandard) {
		if (resultCSV != null) {
			try {
				resultCSV.write(runID + "," +
						term.resultLine(sessionStartTimestamp));
				if(!isStandard){
					resultSQL.write(term.SQLLine(sessionStartTimestamp,transactionCount));
				}
			} catch (IOException ie) {
				log.error("Term-00, " + ie.getMessage());
			}
			catch(Exception e){
				log.error("Term-00, " + e.getMessage());
			}
		}
	}

	private void endReport() {
		long currTimeMillis = System.currentTimeMillis();
		long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
		long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
		double tpmC = (6000000 * fastNewOrderCounter / (currTimeMillis - sessionStartTimestamp)) / 100.0;
		double tpmTotal = (6000000 * transactionCount / (currTimeMillis - sessionStartTimestamp)) / 100.0;
		double vpmTotal = (6000000 * transValCount / (currTimeMillis - sessionStartTimestamp)) / 100.0;// change 11.13
		double vpsTotal = vpmTotal/60;
		double tpsTotal = tpmTotal/60;
		double abortRate = (double)abortCount/transactionCount;
		System.out.println("");


		Collections.sort(latency_queue);
		Collections.sort(E_H_latency_q);
		Collections.sort(H_latency_q);
		Collections.sort(N_latency_q);
		Collections.sort(L_latency_q);

		int queue_lenth = latency_queue.size();
		int e_h_queue_lenth = E_H_latency_q.size();
		int h_queue_lenth = H_latency_q.size();
		int n_queue_lenth = N_latency_q.size();
		int l_queue_lenth = L_latency_q.size();
		double e_h_average = 0;
		double h_average = 0;
		double n_average = 0;
		double l_average = 0;
		e_h_average = get_average(E_H_latency_q);
		h_average = get_average(H_latency_q);
		n_average = get_average(N_latency_q);
		l_average = get_average(L_latency_q);

		

		log.info("Term-00, ");
		log.info("Term-00, ");
		log.info("Term-00, Measured tpmC (NewOrders) = " + tpmC);
		log.info("Term-00, Measured tpmTOTAL = " + tpmTotal);
		log.info("Term-00, Measured vpmTotal = " + vpmTotal);
		log.info("Term-00, Measured tpsTotal = " + tpsTotal);
		log.info("Term-00, Measured vpsTotal = " + vpsTotal);
		log.info("Term-00, Session Start     = " + sessionStart);
		log.info("Term-00, Session End       = " + sessionEnd);
		log.info("Term-00, Session Excute = "+(sessionEndTimestamp-sessionStartTimestamp)+" ms");
		log.info("Term-00, Value Count = " + transValCount);// change 11.13
		log.info("Term-00, Transaction Count = " + (transactionCount - 1));
		log.info("Term-00, Transaction abort rate = " + abortRate);
		if(queue_lenth!=0){
			long p50 = latency_queue.get((int)(queue_lenth*0.5));
			long p99 = latency_queue.get((int)(queue_lenth*0.99));
			long p999 = latency_queue.get((int)(queue_lenth*0.999));
			log.info("Term-00, latency p50 = " + p50);
			log.info("Term-00, latency p99 = " + p99);
			log.info("Term-00, latency p999 = " + p999);
		}		
		if(e_h_queue_lenth!=0){
			long e_h_p99 = E_H_latency_q.get((int)(e_h_queue_lenth*0.99));
			log.info("Term-00, Extra High Priority transactions average latency = " + e_h_average);
			log.info("Term-00, latency p99 = " + e_h_p99);
		}
		if(h_queue_lenth!=0){
			long h_p99 = H_latency_q.get((int)(h_queue_lenth*0.99));
			log.info("Term-00, High Priority transactions average latency = " + h_average);
			log.info("Term-00, latency p99 = " + h_p99);
		}
		if(n_queue_lenth!=0){
			long n_p99 = N_latency_q.get((int)(n_queue_lenth*0.99));
			log.info("Term-00, Normal Priority transactions average latency = " + n_average);
			log.info("Term-00, latency p99 = " + n_p99);
		}
		if(l_queue_lenth!=0){
			long l_p99 = L_latency_q.get((int)(l_queue_lenth*0.99));
			log.info("Term-00, Low Priority transactions average latency = " + l_average);
			log.info("Term-00, latency p99 = " + l_p99);
		}
		for (String key : costPerWorkerload.keySet()) {
			Long value = costPerWorkerload.get(key);
			log.info("executeTime[" + key + "]=" + value.toString());
		}


		for(int i = 0;i<epochTpmTotalRecords.size();i++){
			log.info("Term-00, epoch " + i + " Tpm = " + epochTpmTotalRecords.get(i));
			log.info("Term-00, epoch " + i + " Vpm = " + epochVpmTotalRecords.get(i));
		}
	}

	private void printMessage(String message) {
		log.trace("Term-00, " + message);
	}

	private void errorMessage(String message) {
		log.error("Term-00, " + message);
	}

	private void exit() {
		System.exit(0);
	}

	private String getCurrentTime() {
		return dateFormat.format(new java.util.Date());
	}

	private String getFileNameSuffix() {
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		return dateFormat.format(new java.util.Date());
	}

	synchronized private void updateStatusLine() {
		long currTimeMillis = System.currentTimeMillis();

		if (currTimeMillis > sessionNextTimestamp) {
			StringBuilder informativeText = new StringBuilder("");
			Formatter fmt = new Formatter(informativeText);
			double tpmC = (6000000 * fastNewOrderCounter / (currTimeMillis - sessionStartTimestamp)) / 100.0;
			double tpmTotal = (6000000 * transactionCount / (currTimeMillis - sessionStartTimestamp)) / 100.0;
			if(nextChangeTime != 0){
				if(currTimeMillis>nextChangeTime){
					epochEndTime = currTimeMillis;
					long epochTransCount = transactionCount - lastEpochTransCount;
					long epochValueCount = transValCount - lastEpochValue;

					long deltaTime = epochEndTime-epochStartTime;
					lastEpochTransCount = transactionCount;
					lastEpochValue = transValCount;
					epochStartTime = currTimeMillis;
					double epochtpmTotal = ((6000000*epochTransCount/deltaTime)/100.0);
					double epochvpmTotal = ((6000000*epochValueCount/deltaTime)/100.0);

					nextChangeTime += changeTime*1000;

					epochVpmTotalRecords.add(epochvpmTotal);
					epochTpmTotalRecords.add(epochtpmTotal);
				}

			}
			sessionNextTimestamp += 1000; /* update this every seconds */

			fmt.format("Term-00, Running Average tpmTOTAL: %.2f", tpmTotal);

			/* XXX What is the meaning of these numbers? */
			recentTpmC = (fastNewOrderCounter - sessionNextKounter) * 12;
			recentTpmTotal = (transactionCount - sessionNextKounter) * 12;
			sessionNextKounter = fastNewOrderCounter;
			fmt.format("    Current tpmTOTAL: %d", recentTpmTotal);

			long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
			long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
			fmt.format("    Memory Usage: %dMB / %dMB          ", (totalMem - freeMem), totalMem);

			
			System.out.print(informativeText);
			for (int count = 0; count < 1 + informativeText.length(); count++)
				System.out.print("\b");
		}
	}


	//读取保存的transaction文件
	private void readJson(){
		try{
			SQLFileReader = new BufferedReader(new FileReader(sqlDataJsonPath));
		}
		catch(IOException e){
			errorMessage("Term-00, could not read JsonFile");
		}
	}


	//读取单个transaction
	synchronized public String readJsonLine(){
		String JsonLine = "";
		String tmpLine = "";
		try{
			while((tmpLine = SQLFileReader.readLine())!= null){
				String FirstSeven = tmpLine.length() >= 11 ? tmpLine.substring(0,11) : tmpLine;
				JsonLine+=tmpLine;
				if(FirstSeven.equals("\"transType\"")){
					JsonLine = JsonLine.substring(0,JsonLine.length());//去除“，”
					return JsonLine;
				}
			}
		}
		catch(IOException e){
			errorMessage("Term-00, could not read JsonFile");
		}
		return JsonLine;
	}

	public int getLimitValue(){
		return this.limitValue;
	}

	public long getSessionStart(){
		return this.sessionStartTimestamp;
	}

	private double get_average(Vector<Long> array){
		int len = array.size();
		double average = 0;
		for(int i = 0;i<len;i++){
			average+=array.get(i);
		}
		average = average/len;
		return average;
	}

	public boolean isHotItem(int i_id){
		return this.hot_item.containsKey(i_id);
	}

	synchronized public void updateHotItem(int i_id)throws Exception{
		if(this.hot_item.containsKey(i_id)){
			this.hot_item.get(i_id);
		}
		else{
			this.hot_item.put(i_id, 0.0);
			
		}
	}
}
