package client;/*
 * jTPCCTerminal - Terminal emulator code for jTPCC (transactions)
 *
 * Copyright (C) 2003, Raul Barbosa
 * Copyright (C) 2004-2016, Denis Lussier
 * Copyright (C) 2016, Jan Wieck
 *
 */
import org.apache.log4j.*;
import org.firebirdsql.jdbc.parser.JaybirdSqlParser.deleteStatement_return;

import java.io.*;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import javax.swing.*;
import org.json.JSONObject;

import com.mysql.cj.x.protobuf.MysqlxCrud.Order;

public class jTPCCTerminal implements jTPCCConfig, Runnable
{
    private static org.apache.log4j.Logger log = Logger.getLogger(jTPCCTerminal.class);

    private String terminalName;
    private Connection conn = null;
    private Statement stmt = null;
    private Statement stmt1 = null;
    private ResultSet rs = null;
    private int terminalWarehouseID, terminalDistrictID;
    private boolean terminalWarehouseFixed;
    private int[] paymentWeight, orderStatusWeight, deliveryWeight, stockLevelWeight;
	private int limPerMin_Terminal;
    public  jTPCC parent;
    private jTPCCRandom rnd;
	private long changeTime;
	private int next_transaction_type = TT_RANDOM;
	private double OrderVal;
	private int DeliveryOrderCount = 0;

	private double transVal;//change 11.13
	private double transOrderVal;
	private int is_abort;
    private int transactionCount = 1;
    private int numTransactions;
    private int numWarehouses;
    private int newOrderCounter;
    private long totalTnxs = 1;
    private StringBuffer query = null;
    private int result = 0;
    private volatile boolean stopRunningSignal = false;
	private Vector<Long> latency_queue = new Vector<>();
	private boolean isReadJson;

	private Vector<String> Querylist;
	private boolean isHeap = true;

	private boolean standardSQL;
	private Vector<Long>E_H_latency_q = new Vector<>();
	private Vector<Long>H_latency_q = new Vector<>();
	private Vector<Long>N_latency_q = new Vector<>();
	private Vector<Long>L_latency_q = new Vector<>();
	private int epoch = 0;

	private Map<Integer,Double> reStock_item = new HashMap<>();

    long terminalStartTime = 0;
    long transactionEnd = 0;

    jTPCCConnection db = null;
    int                 dbType = 0;

	public final static int HEAP_MAX_SIZE = 10000;
	public final static int TT_NEW_ORDER = 0,
	TT_PAYMENT = 1,
	TT_ORDER_STATUS = 2,
	TT_STOCK_LEVEL = 4,
	TT_DELIVERY = 3,
	TT_DELIVERY_BG = 5,
	TT_NONE = 6,
	TT_DONE = 7,
	TT_RANDOM = 8,
	TT_TRIGER_RESTOCK = 9;

	public final static int
	EX_HIGH_PRIO = 3, 
	HIGH_PRIO = 2,
	NORMAL_PRIO = 1,
	LOW_PRIO = 0;
	public boolean value_loss;
	public int timecounter;

    public jTPCCTerminal
      (String terminalName, int terminalWarehouseID, int terminalDistrictID,
       Connection conn, int dbType,
       int numTransactions, boolean terminalWarehouseFixed,
       int[] paymentWeight, int[] orderStatusWeight,
       int[] deliveryWeight, int[] stockLevelWeight, int numWarehouses, int limPerMin_Terminal, jTPCC parent, boolean standardSQL,boolean isHeap,boolean isReadJson,long changeTime,boolean value_loss,int timecounter) throws SQLException
    {
	this.terminalName = terminalName;
	this.conn = conn;
	this.dbType = dbType;
	this.stmt = conn.createStatement();
	this.stmt.setMaxRows(200);
	this.stmt.setFetchSize(100);
	this.timecounter = timecounter;
	this.stmt1 = conn.createStatement();
	this.stmt1.setMaxRows(1);

	this.terminalWarehouseID = terminalWarehouseID;
	this.terminalDistrictID = terminalDistrictID;
	this.terminalWarehouseFixed = terminalWarehouseFixed;
	this.parent = parent;
	this.rnd = parent.getRnd().newRandom();
	this.numTransactions = numTransactions;
	this.paymentWeight = paymentWeight;
	this.orderStatusWeight = orderStatusWeight;
	this.deliveryWeight = deliveryWeight;
	this.stockLevelWeight = stockLevelWeight;
	this.numWarehouses = numWarehouses;
	this.newOrderCounter = 0;
	this.limPerMin_Terminal = limPerMin_Terminal;

	this.standardSQL = standardSQL;
	this.isReadJson = isReadJson;
	this.isHeap = isHeap;
	this.changeTime = changeTime;
	this.value_loss = value_loss;

	this.db = new jTPCCConnection(conn, dbType);

	terminalMessage("");
	terminalMessage("Terminal \'" + terminalName + "\' has WarehouseID=" + terminalWarehouseID + " and DistrictID=" + terminalDistrictID + ".");
	terminalStartTime = System.currentTimeMillis();
    }

    public void run()
    {
	try{
		if(dbType == parent.DB_ORACLE){
			jTPCCTData  term = new jTPCCTData(this);
			term.executeOraclePre(db);
		}
		if(!isReadJson){
			if(!standardSQL){
				if(isHeap){
					executeHeapSQL();
				}else{
					executeTransactions(numTransactions);
				}
			}
			else{
					executeStandardTransactions();
			}
		}
		else{
			HeapReadJson();
		}
	}
	catch(Exception e){
		printMessage("");
	    printMessage("An error occurred!");
	    logException(e);
	}


	try
	{
	    printMessage("");
	    printMessage("Closing statement and connection...");

	    stmt.close();
	    conn.close();
	}
	catch(Exception e)
	{
	    printMessage("");
	    printMessage("An error occurred!");
	    logException(e);
	}

	printMessage("");
	printMessage("Terminal \'" + terminalName + "\' finished after " + (transactionCount-1) + " transaction(s).");

	parent.signalTerminalEnded(this, newOrderCounter,latency_queue,E_H_latency_q,H_latency_q,N_latency_q,L_latency_q);
    }

    public void stopRunningWhenPossible()
    {
	stopRunningSignal = true;
	printMessage("");
	printMessage("Terminal received stop signal!");
	printMessage("Finishing current transaction before exit...");
    }

    private void executeTransactions(int numTransactions) throws Exception
    {
	boolean stopRunning = false;

	if(numTransactions != -1)
	    printMessage("Executing " + numTransactions + " transactions...");
	else
	    printMessage("Executing for a limited time...");

	for(int i = 0; (i < numTransactions || numTransactions == -1) && !stopRunning; i++)
	{
		long now_timestamp = System.currentTimeMillis();
		long deltaTime = now_timestamp-terminalStartTime;
		if(changeTime <= 0){
			epoch = 0;
		}
		else{
			epoch = ((int)deltaTime/(int)(changeTime*1000))%2;
		}

	    long transactionType;
	    int skippedDeliveries = 0, newOrder = 0;
	    String transactionTypeName;

	    long transactionStart = System.currentTimeMillis();

	    /*
	     * TPC/C specifies that each terminal has a fixed
	     * "home" warehouse. However, since this implementation
	     * does not simulate "terminals", but rather simulates
	     * "application threads", that association is no longer
	     * valid. In the case of having less clients than
	     * warehouses (which should be the normal case), it
	     * leaves the warehouses without a client without any
	     * significant traffic, changing the overall database
	     * access pattern significantly.
	     */
	    if(!terminalWarehouseFixed)
		terminalWarehouseID = rnd.nextInt(1, numWarehouses);
		// terminalDistrictID = rnd.nextInt(1, 10);

		if(next_transaction_type == TT_RANDOM){
			transactionType = rnd.nextLong(1, 100);
		}
		else{
			switch (next_transaction_type) {
				case TT_PAYMENT:
					transactionType = paymentWeight[epoch];
					break;
				case TT_STOCK_LEVEL:
					transactionType = paymentWeight[epoch] + stockLevelWeight[epoch];
					break;
				case TT_ORDER_STATUS:
					transactionType = paymentWeight[epoch] + stockLevelWeight[epoch] + orderStatusWeight[epoch];
					break;
				case TT_DELIVERY:
					transactionType = paymentWeight[epoch] + stockLevelWeight[epoch] + orderStatusWeight[epoch] + deliveryWeight[epoch];
					break;
				case TT_NEW_ORDER:
					transactionType = 100;
					break;
				case TT_TRIGER_RESTOCK:
					transactionType = -1;
					break;
				default:
					transactionType = rnd.nextLong(1, 100);
					break;
			}
		}
		
		if(transactionType == -1){
			jTPCCTData      term = new jTPCCTData(this);
			term.setNumWarehouses(numWarehouses);
			term.setWarehouse(terminalWarehouseID);
			term.setDistrict(terminalDistrictID);
			try
			{
				term.generateStockLevel(log, rnd, 0,reStock_item);
				term.traceScreen(log);
				term.execute(log, db, rnd,parent.getNoPayCount(),parent.getNewOrderAvgValue(),parent.getPaycounter(),parent.getPayAvgValue());
				transVal = term.getTransVal_real();
				is_abort = term.get_abort();
				latency_queue.add(term.get_latency());
				parent.resultAppend(term,false);
				term.traceScreen(log);
				reStock_item.clear();
			}
			catch (CommitException e)
			{
				continue;
			}
			catch (Exception e)
			{
				log.fatal(e.getMessage());
				e.printStackTrace();
				System.exit(4);
			}
			transactionTypeName = "Triger_Restrock";
		}
	    else if(transactionType <= paymentWeight[epoch]&&this.newOrderCounter>0)
	    {
		jTPCCTData      term = new jTPCCTData(this);
		term.setNumWarehouses(numWarehouses);
		term.setWarehouse(terminalWarehouseID);
		term.setDistrict(terminalDistrictID);
		try
		{
		    term.generatePayment(log, rnd, 0);
		    term.traceScreen(log);
		    term.execute(log, db, rnd,parent.getNoPayCount(),parent.getNewOrderAvgValue(),parent.getPaycounter(),parent.getPayAvgValue());
			OrderVal = term.get_transOrderVal();
			transVal = term.getTransVal_real();
			is_abort = term.get_abort();
			latency_queue.add(term.get_latency());
		    parent.resultAppend(term,false);
		    term.traceScreen(log);
			
		}
		catch (CommitException e)
		{
			continue;
		}
		catch (Exception e)
		{
		    log.fatal(e.getMessage());
		    e.printStackTrace();
		    System.exit(4);
		}
		transactionTypeName = "Payment";
	    }
		else if(transactionType <= paymentWeight[epoch]&&this.newOrderCounter<=0){
			continue;
		}
	    else if(transactionType <= paymentWeight[epoch] + stockLevelWeight[epoch])
	    {
		jTPCCTData      term = new jTPCCTData(this);
		term.setNumWarehouses(numWarehouses);
		term.setWarehouse(terminalWarehouseID);
		term.setDistrict(terminalDistrictID);
		try
		{
		    term.generateStockLevel(log, rnd, 0,reStock_item);
		    term.traceScreen(log);
		    term.execute(log, db, rnd,parent.getNoPayCount(),parent.getNewOrderAvgValue(),parent.getPaycounter(),parent.getPayAvgValue());
			transVal = term.getTransVal_real();
			is_abort = term.get_abort();
			latency_queue.add(term.get_latency());
		    parent.resultAppend(term,false);
		    term.traceScreen(log);
		}
		catch (CommitException e)
		{
			continue;
		}
		catch (Exception e)
		{
		    log.fatal(e.getMessage());
		    e.printStackTrace();
		    System.exit(4);
		}
		transactionTypeName = "Stock-Level";
	    }
	    else if(transactionType <= paymentWeight[epoch] + stockLevelWeight[epoch] + orderStatusWeight[epoch])
	    {
		jTPCCTData      term = new jTPCCTData(this);
		term.setNumWarehouses(numWarehouses);
		term.setWarehouse(terminalWarehouseID);
		term.setDistrict(terminalDistrictID);
		try
		{
		    term.generateOrderStatus(log, rnd, 0);
		    term.traceScreen(log);
		    term.execute(log, db, rnd,parent.getNoPayCount(),parent.getNewOrderAvgValue(),parent.getPaycounter(),parent.getPayAvgValue());
			transVal = term.getTransVal_real();
			is_abort = term.get_abort();
			latency_queue.add(term.get_latency());
		    parent.resultAppend(term,false);
		    term.traceScreen(log);
		}
		catch (CommitException e)
		{
			continue;
		}
		catch (Exception e)
		{
		    log.fatal(e.getMessage());
		    e.printStackTrace();
		    System.exit(4);
		}
		transactionTypeName = "Order-Status";
	    }
	    else if(transactionType <= paymentWeight[epoch] + stockLevelWeight[epoch] + orderStatusWeight[epoch] + deliveryWeight[epoch])
	    {
		jTPCCTData      term = new jTPCCTData(this);
		term.setNumWarehouses(numWarehouses);
		term.setWarehouse(terminalWarehouseID);
		term.setDistrict(terminalDistrictID);
		try
		{
		    term.generateDelivery(log, rnd, 0);
		    term.traceScreen(log);
		    term.execute(log, db, rnd,parent.getNoPayCount(),parent.getNewOrderAvgValue(),parent.getPaycounter(),parent.getPayAvgValue());
			is_abort = term.get_abort();
			latency_queue.add(term.get_latency());
		    parent.resultAppend(term,false);
		    term.traceScreen(log);

		    /*
		     * The old style driver does not have a delivery
		     * background queue, so we have to execute that
		     * part here as well.
		     */
		    jTPCCTData  bg = term.getDeliveryBG();
		    bg.traceScreen(log);
		    bg.execute(log, db, rnd,parent.getNoPayCount(),parent.getNewOrderAvgValue(),parent.getPaycounter(),parent.getPayAvgValue());
		    OrderVal = term.get_transOrderVal();
			DeliveryOrderCount = term.get_deliveryOrderCount();
			transVal = term.getTransVal_real();
			parent.resultAppend(bg,false);
		    bg.traceScreen(log);

		    skippedDeliveries = bg.getSkippedDeliveries();
		}
		catch (CommitException e)
		{
			continue;
		}
		catch (Exception e)
		{
		    log.fatal(e.getMessage());
		    e.printStackTrace();
		    System.exit(4);
		}
		transactionTypeName = "Delivery";
	    }
	    else
	    {
		jTPCCTData      term = new jTPCCTData(this);
		term.setNumWarehouses(numWarehouses);
		term.setWarehouse(terminalWarehouseID);
		term.setDistrict(terminalDistrictID);
		try
		{
		    term.generateNewOrder(log, rnd, 0);
		    term.traceScreen(log);
		    term.execute(log, db, rnd,parent.getNoPayCount(),parent.getNewOrderAvgValue(),parent.getPaycounter(),parent.getPayAvgValue());
			transVal = term.getTransVal_real();
			is_abort = term.get_abort();
			OrderVal = term.get_transOrderVal();
			latency_queue.add(term.get_latency());
		    parent.resultAppend(term,false);
		    term.traceScreen(log);
		}
		catch (CommitException e)
		{
			continue;
		}
		catch (Exception e)
		{
		    log.fatal(e.getMessage());
		    e.printStackTrace();
		    System.exit(4);
		}
		transactionTypeName = "New-Order";
		newOrderCounter++;
		newOrder = 1;
	    }
		
	    long transactionEnd = System.currentTimeMillis();

	    if(!transactionTypeName.equals("Delivery"))
	    {
		parent.signalTerminalEndedTransaction(this.terminalName, transactionTypeName, transactionEnd - transactionStart, null, newOrder,transVal,is_abort,OrderVal,DeliveryOrderCount);//change 11.13
	    }
	    else
	    {
		parent.signalTerminalEndedTransaction(this.terminalName, transactionTypeName, transactionEnd - transactionStart, (skippedDeliveries == 0 ? "None" : "" + skippedDeliveries + " delivery(ies) skipped."), newOrder,transVal,is_abort,OrderVal,DeliveryOrderCount);//change 11.13
	    }
		DeliveryOrderCount = 0;
		OrderVal = 0;

	    if(limPerMin_Terminal>0){
		long elapse = transactionEnd-transactionStart;
		long timePerTx = 60000/limPerMin_Terminal;

		if(elapse<timePerTx){
		    try{
			long sleepTime = timePerTx-elapse;
			Thread.sleep((sleepTime));
		    }
		    catch(Exception e){
		    }
		}
	    }
	    if(stopRunningSignal) stopRunning = true;
	}
    }


    private void error(String type) {
      log.error(terminalName + ", TERMINAL=" + terminalName + "  TYPE=" + type + "  COUNT=" + transactionCount);
	System.out.println(terminalName + ", TERMINAL=" + terminalName + "  TYPE=" + type + "  COUNT=" + transactionCount);
    }


    private void logException(Exception e)
    {
	StringWriter stringWriter = new StringWriter();
	PrintWriter printWriter = new PrintWriter(stringWriter);
	e.printStackTrace(printWriter);
	printWriter.close();
	log.error(stringWriter.toString());
    }


    private void terminalMessage(String message) {
	log.trace(terminalName + ", " + message);
    }


    private void printMessage(String message) {
	log.trace(terminalName + ", " + message);
    }


    void transRollback () {
	try {
	    conn.rollback();
	} catch(SQLException se) {
	    log.error(se.getMessage());
	}
    }

	public Vector<Long> get_latencyQueue(){
		return latency_queue;
	}

	public Vector<Long> get_e_h_latencyQueue(){
		return E_H_latency_q;
	}

	public Vector<Long> get_h_latencyQueue(){
		return H_latency_q;
	}

	public Vector<Long> get_n_latencyQueue(){
		return N_latency_q;
	}

	public Vector<Long> get_l_latencyQueue(){
		return L_latency_q;
	}



    void transCommit() {
	try {
	    conn.commit();
	} catch(SQLException se) {
	    log.error(se.getMessage());
	    transRollback();
	}
    } // end transCommit()


	// void transactionFromJson(String JsonLine){
	// 	int head = JsonLine.indexOf(":")+1;
	// 	int tail = JsonLine.length()-1;
	// 	JsonLine  = JsonLine.substring(head, tail);
	// 	JSONObject SQLJson = new JSONObject(JsonLine);
	// 	System.out.println(SQLJson);
	// 	String QueryString = SQLJson.getString("sql");
	// 	String[] Querys = QueryString.split(";");
	// 	for(String Query : Querys){
	// 			Querylist.add(Query);
	// 	}
	// }


	private void executeStandardTransactions(){
		String SQLString = parent.readJsonLine();
		boolean stopRunning = false;
		int priority;
		while(SQLString != ""&&!stopRunning){
			
			try{
				long transactionStart = System.currentTimeMillis();
				jTPCCTData  term = new jTPCCTData(this); 
				term.executeStandardQuery(log, SQLString, parent.getSessionStart(), db, rnd);
				transVal = term.getTransVal_real();
				term.traceScreen(log);
				long transactionEnd = System.currentTimeMillis();
				String typename = term.getTransType();
				priority = term.get_priority();
				is_abort = term.get_abort();
				if(is_abort == 0){
					latency_queue.add(term.get_transEndTime() - term.get_transGenerateTime());
					switch(priority){
						case EX_HIGH_PRIO:
							E_H_latency_q.add(term.get_transEndTime() - term.get_transGenerateTime());
							break;
						case HIGH_PRIO:
							H_latency_q.add(term.get_transEndTime() - term.get_transGenerateTime());
							break;
						case NORMAL_PRIO:
							N_latency_q.add(term.get_transEndTime() - term.get_transGenerateTime());
							if(term.get_transEndTime() - term.get_transGenerateTime()<0){
								System.out.println("error");
							}
							break;
						case LOW_PRIO:
							L_latency_q.add(term.get_transEndTime() - term.get_transGenerateTime());
							break;
					}
				}
				parent.resultAppend(term,true);
				int neworder = 0;
				if(typename.equals("New-Order")){
					neworder = 1;
				}
				parent.signalTerminalEndedTransaction(this.terminalName, typename, transactionEnd - transactionStart, null, neworder,transVal,is_abort,OrderVal,DeliveryOrderCount);
				if(stopRunningSignal) stopRunning = true;
			}
			catch (CommitException e)
			{
				continue;
			}
			catch (Exception e)
			{
				log.fatal(e.getMessage());
				e.printStackTrace();
				System.exit(4);
			}
			SQLString = parent.readJsonLine();
		}
	}
	
	private void HeapReadJson(){
		String SQLString = parent.readJsonLine();
		boolean stopRunning = false;
		while(SQLString != ""&&!stopRunning){
			int Current_size = parent.Tree.getSize();
			if(Current_size>=HEAP_MAX_SIZE){// busy wait
				if(stopRunningSignal) {
					stopRunning = true;
					// break;
				}
				// try{
				// 	Thread.sleep(1);
				// }
				// catch(InterruptedException ie){
				// 	ie.printStackTrace();
				// }
				continue;
				
			}
			int head = SQLString.indexOf(":")+1;
			int tail = SQLString.length()-1;
			SQLString  = SQLString.substring(head, tail);
			JSONObject SQLJson = new JSONObject(SQLString);
			String ValueString = SQLJson.getString("value");
			String QueryString = SQLJson.getString("sql");
			String type = SQLJson.getString("transType");
			String arriveTime = SQLJson.getString("generateTime");
			String PriorityString = SQLJson.getString("priority");
			long generateTime = Long.parseLong(arriveTime);
			int transType;
			int priority = Integer.parseInt(PriorityString);
			double transVal = Double.parseDouble(ValueString);
			if(type.equals("New-Order")){
				transType = TT_NEW_ORDER;
			}
			else if(type.equals("Payment")){
				transType = TT_PAYMENT;
			}
			else if(type.equals("Stock-Level")){
				transType = TT_STOCK_LEVEL;
			}
			else if(type.equals("Order-Status")){
				transType = TT_ORDER_STATUS;
			}
			else{
				transType = TT_DELIVERY;
			}
			long CurrentTime = System.currentTimeMillis();
			long timeDelta = CurrentTime - parent.getSessionStart();
			while(timeDelta < generateTime){
				CurrentTime = System.currentTimeMillis();
				timeDelta = CurrentTime - parent.getSessionStart();
			}//busy wait
			TreeNode node = new TreeNode(QueryString, transType, transVal,generateTime,priority);
			parent.Tree.add(node);
			SQLString = parent.readJsonLine();
			// try{
			// 	Thread.sleep(10);
			// }
			// catch(InterruptedException ie){
			// 	ie.printStackTrace();
			// }
			if(stopRunningSignal) {
				stopRunning = true;
				// break;
			}
		}
		parent.Tree.setIsAllLoad(true);
	}


	private void executeHeapSQL(){
		try{
			String sqlString;
			double transVal;
			int transType;
			boolean stopRunning = false;
			long generateTime;
			int priority;
			while(true&&!stopRunning){
				if(parent.Tree.getIsAllLoad()&&parent.Tree.getSize() == 0){
					break;
				}
				TreeNode node = parent.Tree.pop();
				if(node == null){
					Thread.sleep(1);
					continue;
				}
				else{
					sqlString = node.getSQL();
					transVal = node.getTransVal();
					transType = node.getTransType();
					generateTime = node.getGenerateTime();
					priority = node.getPriority();
					jTPCCTData term = new jTPCCTData(this);
					long transactionStart = System.currentTimeMillis();
					term.executeStandardQuery_Heap(log, sqlString,transType,transVal,generateTime,parent.getSessionStart(),priority,db,rnd);
					transVal = term.getTransVal_real();
					is_abort = term.get_abort();
					term.traceScreen(log);
					
					long transactionEnd = term.get_transEndTime();
					String typename = term.getTransType();
					parent.resultAppend(term,true);
					int neworder = 0;
					if(typename.equals("New-Order")){
						neworder = 1;
					}
					if(is_abort == 0){
						latency_queue.add(term.get_transEndTime() - (parent.getSessionStart()+generateTime));
						switch(priority){
							case EX_HIGH_PRIO:
								E_H_latency_q.add(term.get_transEndTime() - (parent.getSessionStart()+generateTime));
								break;
							case HIGH_PRIO:
								H_latency_q.add(term.get_transEndTime() - (parent.getSessionStart()+generateTime));
								break;
							case NORMAL_PRIO:
								N_latency_q.add(term.get_transEndTime() - (parent.getSessionStart()+generateTime));
								break;
							case LOW_PRIO:
								L_latency_q.add(term.get_transEndTime() - (parent.getSessionStart()+generateTime));
								break;
						}
					}
					parent.signalTerminalEndedTransaction(this.terminalName, typename, transactionEnd - transactionStart, null, neworder,transVal,is_abort,OrderVal,DeliveryOrderCount);
					if(stopRunningSignal) stopRunning = true;
				
				}


			}
		}catch(Exception e){
			e.printStackTrace();
		}
	}

	public void set_next_txn_type(int type,Map<Integer,Double>reStock_item){
		this.next_transaction_type = type;
		this.reStock_item = reStock_item;
	}
}
