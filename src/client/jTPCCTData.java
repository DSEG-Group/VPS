package client;/*
				* jTPCCTData - The simulated terminal input/output data.
				*
				* Copyright (C) 2004-2016, Denis Lussier
				* Copyright (C) 2016, Jan Wieck
				*
				*/

import org.apache.log4j.*;
import server.VPSServer;

import java.util.*;
import java.sql.*;
import org.json.JSONObject;

import oracle.jdbc.OraclePreparedStatement;



public class jTPCCTData {
	protected int numWarehouses = 0;
	
	private 	  int with_prio = 0;
	private 	  int timecounter = 0;
	private		  boolean value_loss = true;
	private       jTPCCTerminal parent;
	private 	  List<Object> params =new ArrayList<>();
	private 	  double transOrderVal = 0;
	private 	  int DeliveryOrderCount = 0;

	public final static int DB_UNKNOWN = 0,
			DB_FIREBIRD = 1,
			DB_ORACLE = 2,
			DB_POSTGRES = 3,
			DB_MYSQL = 4,
			DB_COCKROACH = 5;

	public final static int
			EX_HIGH_PRIO = 3, 
			HIGH_PRIO = 2,
			NORMAL_PRIO = 1,
			LOW_PRIO = 0;

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

	public final static String transTypeNames[] = {
			"NEW_ORDER", "PAYMENT", "ORDER_STATUS", "STOCK_LEVEL",
			"DELIVERY", "DELIVERY_BG", "NONE", "DONE" };

	public long sched_fuzz;
	public jTPCCTData term_left;
	public jTPCCTData term_right;
	public int tree_height;
	private int abort = 0;
	private double loss_v = 0;
	private int extreme_high_value_rate = 1;
	private int high_value_rate = 19;
	private int normal_value_rate = 40;
	private int low_value_rate = 40;
	private int priority = 0;
	private String SQLString = "";
	private String inputdata = "";

	private int transType;
	private long transDue;
	private long transStart;
	private long transEnd;
	private boolean transRbk;
	private String transError;
	private long transGeneratime = 0;
	private double transVal_real;
	private long timeDelta;// 时差
	private double abortPoss;
	private long sessionTimestamp;

	private int terminalWarehouse = 0;
	private int terminalDistrict = 0;
	private long timeCounterNow;// 确认当前时间

	private NewOrderData newOrder = null;
	private PaymentData payment = null;
	private OrderStatusData orderStatus = null;
	private StockLevelData stockLevel = null;
	private DeliveryData delivery = null;
	private DeliveryBGData deliveryBG = null;

	private static Object traceLock = new Object();

	private StringBuffer resultSB = new StringBuffer();
	private Formatter resultFmt = new Formatter(resultSB);

	private Vector<String> Querylist = new Vector<String>();
	private double NewOrderAvgValue;
	private long notPayCounter;
	private double PayAvgValue;
	private long PayCounter;


	public jTPCCTData(jTPCCTerminal parent){
		this.parent = parent;
		value_loss = parent.value_loss;
		timecounter = parent.timecounter;
	}

	public jTPCCTData(){
		this.newOrder = new NewOrderData();
		this.payment = new PaymentData();
		this.orderStatus = new OrderStatusData();
		this.stockLevel = new StockLevelData();
		this.delivery = new DeliveryData();
	}

	public void setNumWarehouses(int num) {
		numWarehouses = num;
	}

	public void setWarehouse(int warehouse) {
		terminalWarehouse = warehouse;
	}

	public int getWarehouse() {
		return terminalWarehouse;
	}

	public void setDistrict(int district) {
		terminalDistrict = district;
	}

	public int getDistrict() {
		return terminalDistrict;
	}

	public void execute(Logger log, jTPCCConnection db, jTPCCRandom rnd,long notPayCounter,double NewOrderAvgValue,long PayCounter,double PayAvgValue)
			throws Exception {
		transStart = System.currentTimeMillis();
		this.notPayCounter = notPayCounter;
		this.NewOrderAvgValue = NewOrderAvgValue;
		this.PayAvgValue = PayAvgValue;
		this.PayCounter = PayCounter;
		if (transDue == 0)
			transDue = transStart;

		switch (transType) {
			case TT_NEW_ORDER:
				executeNewOrder(log, db, rnd);
				break;

			case TT_PAYMENT:
				executePayment(log, db, rnd);
				break;

			case TT_ORDER_STATUS:
				executeOrderStatus(log, db);
				break;

			case TT_STOCK_LEVEL:
				executeStockLevel(log, db);
				break;

			case TT_DELIVERY:
				executeDelivery(log, db);
				break;

			case TT_DELIVERY_BG:
				executeDeliveryBG(log, db);
				break;

			default:
				throw new Exception("Unknown transType " + transType);
		}

		transEnd = System.currentTimeMillis();
	}

	public double getTransVal_real()
			throws Exception {
		return this.transVal_real;
	}

	public void traceScreen(Logger log)
			throws Exception {
		StringBuffer sb = new StringBuffer();
		Formatter fmt = new Formatter(sb);

		StringBuffer screenSb[] = new StringBuffer[23];
		Formatter screenFmt[] = new Formatter[23];
		for (int i = 0; i < 23; i++) {
			screenSb[i] = new StringBuffer();
			screenFmt[i] = new Formatter(screenSb[i]);
		}

		if (!log.isTraceEnabled())
			return;

		if (transType < TT_NEW_ORDER || transType > TT_DONE)
			throw new Exception("Unknown transType " + transType);

		synchronized (traceLock) {
			fmt.format("==== %s %s ==== Terminal %d,%d =================================================",
					transTypeNames[transType],
					(transEnd == 0) ? "INPUT" : "OUTPUT",
					terminalWarehouse, terminalDistrict);
			sb.setLength(79);
			log.trace(sb.toString());
			sb.setLength(0);

			fmt.format("---- Due:   %s", (transDue == 0) ? "N/A" : new Timestamp(transDue).toString());
			log.trace(sb.toString());
			sb.setLength(0);

			fmt.format("---- Start: %s", (transStart == 0) ? "N/A" : new Timestamp(transStart).toString());
			log.trace(sb.toString());
			sb.setLength(0);

			fmt.format("---- End:   %s", (transEnd == 0) ? "N/A" : new Timestamp(transEnd).toString());
			log.trace(sb.toString());
			sb.setLength(0);

			if (transError != null) {
				fmt.format("#### ERROR: %s", transError);
				log.trace(sb.toString());
				sb.setLength(0);
			}

			log.trace("-------------------------------------------------------------------------------");

			switch (transType) {
				case TT_NEW_ORDER:
					traceNewOrder(log, screenFmt);
					break;

				case TT_PAYMENT:
					tracePayment(log, screenFmt);
					break;

				case TT_ORDER_STATUS:
					traceOrderStatus(log, screenFmt);
					break;

				case TT_STOCK_LEVEL:
					traceStockLevel(log, screenFmt);
					break;

				case TT_DELIVERY:
					traceDelivery(log, screenFmt);
					break;

				case TT_DELIVERY_BG:
					traceDeliveryBG(log, screenFmt);
					break;

				default:
					throw new Exception("Unknown transType " + transType);
			}

			for (int i = 0; i < 23; i++) {
				if (screenSb[i].length() > 79)
					screenSb[i].setLength(79);
				log.trace(screenSb[i].toString());
			}

			log.trace("-------------------------------------------------------------------------------");
			log.trace("");
		}
	}

	public String resultLine(long sessionStart) {
		String line;
		transDue = transStart;
		resultFmt.format("%d,%d,%d,%d,%d,%s,%d,%d,%.2f,%d,%d\n",
				transGeneratime - sessionStart,
				transStart - sessionStart,
				transEnd - sessionStart,
				transEnd - transDue,
				transEnd - transStart,
				transTypeNames[transType],
				(transRbk) ? 1 : 0,
				(transType == TT_DELIVERY_BG) ? getSkippedDeliveries() : 0,
				transVal_real,
				abort,
				priority,
				(transError == null) ? 0 : 1);
		line = resultSB.toString();
		resultSB.setLength(0);
		return line;
	}

	public String SQLLine(long sessionStart,long i)throws Exception{
		if(transType != TT_DELIVERY){
			if(SQLString!=""){
				SQLString = i + ":\n{\"sql\":\"BEGIN;\n" + SQLString;
				SQLString += "\"value\":"+"\""+transVal_real+"\",\n";
				SQLString += "\"priority\":\""+this.priority+"\",\n";
				SQLString += "\"generateTime\":\""+(this.transGeneratime-sessionStart)+"\",\n";
				switch (transType) {
					case TT_NEW_ORDER:
						SQLString += "\"transType\":\"New-Order\",\n";
						break;
					case TT_PAYMENT:
						SQLString += "\"transType\":\"Payment\",\n";
						break;
					case TT_DELIVERY_BG:
						SQLString += "\"transType\":\"Delivery\",\n";
						break;
					case TT_ORDER_STATUS:
						SQLString += "\"transType\":\"Order-Status\",\n";
						break;
					case TT_STOCK_LEVEL:
						SQLString += "\"transType\":\"Stock-Level\",\n";
						break;
					default:
						throw new Exception("Unknown transType " + transType);
				}
				SQLString += "\"input\":\""+ inputdata+"\",\n";
				SQLString += "\"context\":\""+
							String.valueOf(this.notPayCounter)+","+
							String.valueOf(this.NewOrderAvgValue)+","+
							String.valueOf(this.PayCounter)+","+
							String.valueOf(this.PayAvgValue)+"\"},\n";
			}
			return SQLString;
		}
		else{
			return "";
		}
		
	}

	public long get_latency(){
		return transEnd-transGeneratime;
	}

	/*
	 * **********************************************************************
	 * **********************************************************************
	 * ***** NEW_ORDER related methods and subclass. ************************
	 * **********************************************************************
	 *********************************************************************/
	public void generateNewOrder(Logger log, jTPCCRandom rnd, long due) {
		int o_ol_cnt;
		int i = 0;

		transType = TT_NEW_ORDER;
		transDue = due;
		transStart = 0;
		transEnd = 0;
		transRbk = false;
		transError = null;
		transGeneratime = System.currentTimeMillis();

		newOrder = new NewOrderData();
		payment = null;
		orderStatus = null;
		stockLevel = null;
		delivery = null;
		deliveryBG = null;

		newOrder.w_id = terminalWarehouse; // 2.4.1.1
		newOrder.d_id = rnd.nextInt(1, 10); // 2.4.1.2
		newOrder.c_id = rnd.getCustomerID();
		o_ol_cnt = rnd.nextInt(5, 15); // 2.4.1.3

		int value_level = rnd.nextInt(1, 100);

		transVal_real = 0;
		newOrder.o_ol_cnt = o_ol_cnt;
		while (i < o_ol_cnt) // 2.4.1.5 调整order的分布。
		{
			if (value_level <= this.extreme_high_value_rate) {
				newOrder.ol_i_id[i] = rnd.getItemExtremeHighValueID();
				priority = EX_HIGH_PRIO;
			} else {
				if (value_level > this.extreme_high_value_rate &&
						value_level <= this.high_value_rate + this.extreme_high_value_rate) {
					newOrder.ol_i_id[i] = rnd.getItemHighValueID();
					priority = HIGH_PRIO;
				} else {
					if (value_level > this.high_value_rate + this.extreme_high_value_rate &&
							value_level <= this.high_value_rate + this.extreme_high_value_rate
									+ this.normal_value_rate) {
						newOrder.ol_i_id[i] = rnd.getNormalValueID();
						priority = NORMAL_PRIO;
					} else {
						newOrder.ol_i_id[i] = rnd.getLowValueID();
						priority = LOW_PRIO;
					}
				}
			}
			if (rnd.nextInt(1, 100) <= 99)
				newOrder.ol_supply_w_id[i] = terminalWarehouse;
			else
				newOrder.ol_supply_w_id[i] = rnd.nextInt(1, numWarehouses);
			newOrder.ol_quantity[i] = rnd.nextInt(1, 10);
			newOrder.found[i] = false;
			i++;
		}

		if (rnd.nextInt(1, 100) == 1) // 2.4.1.4
		{
			newOrder.ol_i_id[i - 1] += (rnd.nextInt(1, 9) * 1000000);
			transRbk = true;
		}

		// Zero out remainint lines
		while (i < 15) {
			newOrder.ol_i_id[i] = 0;
			newOrder.ol_supply_w_id[i] = 0;
			newOrder.ol_quantity[i] = 0;
			i++;
		}

	}

	public void executeNewOrder(Logger log, jTPCCConnection db, jTPCCRandom rnd)
			throws Exception {
		PreparedStatement stmt;
		PreparedStatement insertOrderLineBatch;
		ResultSet rs;
		transVal_real = 0;
		int o_id;
		int o_all_local = 1;
		long o_entry_d;
		int ol_cnt;
		double total_amount = 0.0;
		int dbType;
		transOrderVal = 0;
		inputdata = String.valueOf(newOrder.w_id)+"," + 
					String.valueOf(newOrder.d_id)+"," +
					String.valueOf(newOrder.c_id)+",0";
		// for(int i = 0;i<newOrder.o_ol_cnt;i++){
		// 	inputdata = ","+String.valueOf(newOrder.ol_i_id[i])+
		// 				","+String.valueOf(newOrder.i_price[i])+
		// 				","+String.valueOf(newOrder.ol_quantity[i]);
		// }

		int ol_seq[] = new int[15];
				
		// The o_entry_d is now.
		o_entry_d = System.currentTimeMillis();
		newOrder.o_entry_d = new Timestamp(o_entry_d).toString();
		dbType = db.getdbtype();
		/*
		 * When processing the order lines we must select the STOCK rows
		 * FOR UPDATE. This is because we must perform business logic
		 * (the juggling with the S_QUANTITY) here in the application
		 * and cannot do that in an atomic UPDATE statement while getting
		 * the original value back at the same time (UPDATE ... RETURNING
		 * may not be vendor neutral). This can lead to possible deadlocks
		 * if two transactions try to lock the same two stock rows in
		 * opposite order. To avoid that we process the order lines in
		 * the order of the order of ol_supply_w_id, ol_i_id.
		 */
		for (ol_cnt = 0; ol_cnt < 15 && newOrder.ol_i_id[ol_cnt] != 0; ol_cnt++) {
			ol_seq[ol_cnt] = ol_cnt;

			// While looping we also determine o_all_local.
			if (newOrder.ol_supply_w_id[ol_cnt] != newOrder.w_id)
				o_all_local = 0;
		}

		for (int x = 0; x < ol_cnt - 1; x++) {
			for (int y = x + 1; y < ol_cnt; y++) {
				if (newOrder.ol_supply_w_id[ol_seq[y]] < newOrder.ol_supply_w_id[ol_seq[x]]) {
					int tmp = ol_seq[x];
					ol_seq[x] = ol_seq[y];
					ol_seq[y] = tmp;
				} else if (newOrder.ol_supply_w_id[ol_seq[y]] == newOrder.ol_supply_w_id[ol_seq[x]] &&
						newOrder.ol_i_id[ol_seq[y]] < newOrder.ol_i_id[ol_seq[x]]) {
					int tmp = ol_seq[x];
					ol_seq[x] = ol_seq[y];
					ol_seq[y] = tmp;
				}
			}
		}

		// The above also provided the output value for o_ol_cnt;
		newOrder.o_ol_cnt = ol_cnt;
		dbType = db.getdbtype();
		try {
			if(with_prio == 1){
				if(dbType == DB_COCKROACH){
					switch (priority) {
						case HIGH_PRIO:
							stmt = db.stmtSetPriorityHigh;
							stmt.execute();
							break;
						case NORMAL_PRIO:
							stmt = db.stmtSetPriorityNormal;
							stmt.execute();
							break;
						case LOW_PRIO:
							stmt = db.stmtSetPriorityLow;
							stmt.execute();
							break;
						default:
							break;
					}
				}
			}

			// Retrieve the required data from DISTRICT
			stmt = db.stmtNewOrderSelectDist;
			stmt.setInt(1, newOrder.w_id);
			stmt.setInt(2, newOrder.d_id);
			params.clear();
			params.add(newOrder.w_id);
			params.add(newOrder.d_id);
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }
			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
					break;
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			if (!rs.next()) {
				rs.close();
				throw new SQLException("District for" +
						" W_ID=" + newOrder.w_id +
						" D_ID=" + newOrder.d_id + " not found");
			}
			newOrder.d_tax = rs.getDouble("d_tax");
			newOrder.o_id = rs.getInt("d_next_o_id");
			o_id = newOrder.o_id;
			rs.close();

			// Retrieve the required data from CUSTOMER and WAREHOUSE
			stmt = db.stmtNewOrderSelectWhseCust;
			stmt.setInt(1, newOrder.w_id);
			stmt.setInt(2, newOrder.d_id);
			stmt.setInt(3, newOrder.c_id);
			params.clear();
			params.add(newOrder.w_id);
			params.add(newOrder.d_id);
			params.add(newOrder.c_id);
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
					break;
				default:
					break;
			}


			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}


			if (!rs.next()) {
				rs.close();
				throw new SQLException("Warehouse or Customer for" +
						" W_ID=" + newOrder.w_id +
						" D_ID=" + newOrder.d_id +
						" C_ID=" + newOrder.c_id + " not found");
			}
			newOrder.w_tax = rs.getDouble("w_tax");
			newOrder.c_last = rs.getString("c_last");
			newOrder.c_credit = rs.getString("c_credit");
			newOrder.c_discount = rs.getDouble("c_discount");
			rs.close();

			// Update the DISTRICT bumping the D_NEXT_O_ID
			stmt = db.stmtNewOrderUpdateDist;
			stmt.setInt(1, newOrder.w_id);
			stmt.setInt(2, newOrder.d_id);
			params.clear();
			params.add(newOrder.w_id);
			params.add(newOrder.d_id);
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
					break;
				default:
					break;
			}

			stmt.executeUpdate();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			// Insert the ORDER row
			stmt = db.stmtNewOrderInsertOrder;
			Timestamp time_now = new Timestamp(System.currentTimeMillis());
			stmt.setInt(1, o_id);
			stmt.setInt(2, newOrder.d_id);
			stmt.setInt(3, newOrder.w_id);
			stmt.setInt(4, newOrder.c_id);
			stmt.setTimestamp(5, time_now);
			stmt.setInt(6, ol_cnt);
			stmt.setInt(7, o_all_local);
			params.clear();
			params.add(o_id);
			params.add(newOrder.d_id);
			params.add(newOrder.w_id);
			params.add(newOrder.c_id);
			params.add(time_now);
			params.add(ol_cnt);
			params.add(o_all_local);
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
					break;
				default:
					break;
			}

			int row = stmt.executeUpdate();
			if(row < 1){
				System.out.println("Insert the order row false");
			}

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			// Insert the NEW_ORDER row
			stmt = db.stmtNewOrderInsertNewOrder;
			stmt.setInt(1, o_id);
			stmt.setInt(2, newOrder.d_id);
			stmt.setInt(3, newOrder.w_id);
			params.clear();
			params.add(o_id);
			params.add(newOrder.d_id);
			params.add(newOrder.w_id);
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt.executeUpdate();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			// Per ORDER_LINE
			insertOrderLineBatch = db.stmtNewOrderInsertOrderLine;
			int seq0 = ol_seq[0];
			boolean distinct_item = true;
			HashSet<Integer> itemIds = new HashSet<Integer>();
			for (int i = 0; i < ol_cnt; i++) {
				int seq = ol_seq[i];
				itemIds.add(Integer.valueOf(newOrder.ol_i_id[seq]));
			}
			String distName = "s_dist_0" + String.valueOf(newOrder.d_id);
			if (newOrder.d_id > 9) {
				distName = "s_dist_" + String.valueOf(newOrder.d_id);
			}
			stmt = db.stmtNewOrderSelectItemBatch[itemIds.size()];
			HashMap<Integer, NewOrderItem> itemMap = new HashMap<Integer, NewOrderItem>();
			int i_idx = 0;
			params.clear();
			for (Integer x : itemIds) {
				i_idx++;
				stmt.setInt(i_idx, x.intValue());
				params.add(x.intValue());
			}

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }
			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			while (rs.next()) {
				int i_id = rs.getInt("i_id");
				NewOrderItem item = new NewOrderItem();
				item.i_id = i_id;
				item.i_price = rs.getDouble("i_price");
				item.i_name = rs.getString("i_name");
				item.i_data = rs.getString("i_data");
				itemMap.put(i_id, item);
			}
			rs.close();
			for (int i = 0; i < ol_cnt; i++) {
				int seq = ol_seq[i];
				int i_id = newOrder.ol_i_id[seq];
				NewOrderItem item = itemMap.get(i_id);

				if (item == null) {
					if (transRbk && (i_id < 1 ||
							i_id > 100000)) {
						/*
						 * Clause 2.4.2.3 mandates that the entire
						 * transaction profile up to here must be executed
						 * before we can roll back, except for retrieving
						 * the missing STOCK row and inserting this
						 * ORDER_LINE row. Note that we haven't updated
						 * STOCK rows or inserted any ORDER_LINE rows so
						 * far, we only batched them up. So we must do
						 * that now in order to satisfy 2.4.2.3.
						 */
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
					// This ITEM should have been there.
					throw new Exception("ITEM " + newOrder.ol_i_id[seq] +
							" not fount");
				}
			}

			stmt = db.stmtNewOrderSelectStockBatch[ol_cnt];
			params.clear();
			for (int i = 0; i < ol_cnt; ++i) {
				int seq = ol_seq[i];
				stmt.setInt(i * 2 + 1, newOrder.ol_supply_w_id[seq]);
				stmt.setInt(i * 2 + 2, newOrder.ol_i_id[seq]);
				params.add(newOrder.ol_supply_w_id[seq]);
				params.add(newOrder.ol_i_id[seq]);
			}

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			while (rs.next()) {
				int i_id = rs.getInt("s_i_id");
				int w_id = rs.getInt("s_w_id");
				NewOrderItem item = itemMap.get(i_id);

				// There may be two item having the same supply warehouse.
				for (int i = 0; i < ol_cnt; i++) {
					int seq = ol_seq[i];
					if (newOrder.ol_i_id[seq] == i_id && newOrder.ol_supply_w_id[seq] == w_id) {
						newOrder.s_quantity[seq] = rs.getInt("s_quantity");
						newOrder.dist_value[seq] = rs.getString(distName);
						newOrder.found[seq] = true;
						if (item != null) {
							newOrder.i_price[seq] = item.i_price;
							newOrder.ol_amount[seq] = item.i_price * newOrder.ol_quantity[seq];
							VPSServer.updateHotItem(item.i_id, item.i_price);	
							if (item.i_data.contains("ORIGINAL") &&
									rs.getString("s_data").contains("ORIGINAL"))
								newOrder.brand_generic[seq] = new String("B");
							else
								newOrder.brand_generic[seq] = new String("G");
						}
					}
				}
			}
			rs.close();
			int flag = 0;//用于标记该订单是否有商品需要补货
			Map<Integer,Double>reStock_item = new HashMap<>();

			for (int i = 0; i < ol_cnt; i++) {
				int ol_number = i + 1;
				int seq = ol_seq[i];
				if (!newOrder.found[seq]) {
					throw new Exception("STOCK with" +
							" S_W_ID=" + newOrder.ol_supply_w_id[seq] +
							" S_I_ID=" + newOrder.ol_i_id[seq] +
							" not fount");
				}

				// parent.parent.updateHotItem(newOrder.ol_i_id[seq]);
				

				total_amount += newOrder.ol_amount[seq] *
						(1.0 - newOrder.c_discount) *
						(1.0 + newOrder.w_tax + newOrder.d_tax);
				transOrderVal += newOrder.ol_amount[seq];
				stmt = db.stmtNewOrderUpdateStock;
				params.clear();
				// Update the STOCK row.
				if (newOrder.s_quantity[seq] >= newOrder.ol_quantity[seq]){
					stmt.setInt(1, newOrder.s_quantity[seq] -
							newOrder.ol_quantity[seq]);
					params.add(newOrder.s_quantity[seq] - newOrder.ol_quantity[seq]);
				}
				else
					{
						stmt.setInt(1, newOrder.s_quantity[seq]);//自动补货
						params.add(newOrder.s_quantity[seq]);
						flag = 1;
						reStock_item.put(newOrder.ol_i_id[seq],newOrder.i_price[seq]);
					}
				stmt.setInt(2, newOrder.ol_quantity[seq]);
				params.add(newOrder.ol_quantity[seq]);
				if (newOrder.ol_supply_w_id[seq] == newOrder.w_id){
					stmt.setInt(3, 0);
					params.add(0);
				}
				else{
					stmt.setInt(3, 1);
					params.add(0);
				}
				stmt.setInt(4, newOrder.ol_supply_w_id[seq]);
				stmt.setInt(5, newOrder.ol_i_id[seq]);
				params.add(newOrder.ol_supply_w_id[seq]);
				params.add(newOrder.ol_i_id[seq]);

				// if(dbType == DB_MYSQL){
				// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
				// }
				// else{
				// 	SQLString += stmt.toString()+";\n";
				// }
				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}

				stmt.executeUpdate();

				// timeCounterNow = System.currentTimeMillis();
				// timeDelta = timeCounterNow - transStart;
				// if (timeDelta > 200) {
				// 	abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
				// 	if (abortPoss > 1) {
				// 		abortPoss = 1;
				// 	}
				// 	int poss = rnd.nextInt(1, 100);
				// 	if (poss < 100 * abortPoss) {
				// 		abort = 1;
				// 		db.rollback();
				// 		insertOrderLineBatch.clearBatch();
				// 		return;
				// 	}
				// }

				// Insert the ORDER_LINE row.
				insertOrderLineBatch.setInt(1, o_id);
				insertOrderLineBatch.setInt(2, newOrder.d_id);
				insertOrderLineBatch.setInt(3, newOrder.w_id);
				insertOrderLineBatch.setInt(4, ol_number);
				insertOrderLineBatch.setInt(5, newOrder.ol_i_id[seq]);
				insertOrderLineBatch.setInt(6, newOrder.ol_supply_w_id[seq]);
				insertOrderLineBatch.setInt(7, newOrder.ol_quantity[seq]);
				insertOrderLineBatch.setDouble(8, newOrder.ol_amount[seq]);
				insertOrderLineBatch.setString(9, newOrder.dist_value[seq]);
				params.clear();
				params.add(o_id);
				params.add(newOrder.d_id);
				params.add(newOrder.w_id);
				params.add(ol_number);
				params.add(newOrder.ol_i_id[seq]);
				params.add(newOrder.ol_supply_w_id[seq]);
				params.add(newOrder.ol_quantity[seq]);
				params.add(newOrder.ol_amount[seq]);
				params.add(newOrder.dist_value[seq]);
				// if(dbType == DB_MYSQL){
				// 	SQLString += insertOrderLineBatch.toString().replaceFirst("^\\S+\\s", "")+";\n";
				// }
				// else{
				// 	SQLString += insertOrderLineBatch.toString()+";\n";
				// }

				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}

				insertOrderLineBatch.addBatch();
			}
			// if(flag == 1){
			// 	parent.set_next_txn_type(TT_TRIGER_RESTOCK,reStock_item);
			// 	SQLString += "Abort;\",\n";//商品不够，则进行下一个事务执行补货。
			// 	insertOrderLineBatch.clearBatch();
			// 	db.rollback();
			// 	abort = 1;
			// 	return;	
			// }

			// All done ... execute the batches.
			insertOrderLineBatch.executeBatch();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			insertOrderLineBatch.clearBatch();


			stmt = db.stmtNewOrderSelectCustomer;
			stmt.setInt(1, newOrder.w_id);
			stmt.setInt(2, newOrder.d_id);
			stmt.setInt(3, newOrder.c_id);

			rs = stmt.executeQuery();

			if(!rs.next()){
				throw new Exception("Customer with" +
				" W_ID=" + newOrder.w_id +
				" D_ID=" + newOrder.d_id +
				" C_ID=" + newOrder.c_id +
				" not fount");
			}

			double c_order_price = rs.getInt("c_order_price_cnt");
			
			double alpha = 0.7;
			double beta = 0.3;
			transVal_real = alpha*total_amount+beta*c_order_price;//把顾客过去的订单历史也考虑在内。
			c_order_price = (newOrder.total_amount + c_order_price)/2;


			newOrder.execution_status = new String("Order placed");
			newOrder.total_amount = total_amount;
			

			stmt = db.stmtNewOrderUpdateCustomer;
			stmt.setDouble(1,newOrder.total_amount);
			stmt.setDouble(2,c_order_price);
			stmt.setInt(3,newOrder.w_id);
			stmt.setInt(4,newOrder.d_id);
			stmt.setInt(5,newOrder.c_id);
			params.clear();
			params.add(newOrder.total_amount);
			params.add(c_order_price);
			params.add(newOrder.w_id);
			params.add(newOrder.d_id);
			params.add(newOrder.c_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt.executeUpdate();

			//直接损失价值
			if(value_loss){
				timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - transStart;
					if (timeDelta > 200) {
						double valloss = transVal_real*(timeDelta - 200) / (0.1 * 200) * 0.008;
						if(valloss<transVal_real){
							transVal_real = transVal_real-transVal_real*(timeDelta - 200) / (0.1 * 200) * 0.008;
						}
						else{
							transVal_real = 0;
						}
					}
			}
			db.commit();
			for(int i = 0;i<newOrder.o_ol_cnt;i++){
				inputdata += ","+String.valueOf(newOrder.ol_i_id[i])+
							","+String.valueOf(newOrder.ol_quantity[i]);
			}
			SQLString += "COMMIT;\",\n";


			if(transVal_real>=30000){
				this.priority = EX_HIGH_PRIO;
			}
			else if(transVal_real>=5000){
				this.priority = HIGH_PRIO;
			}
			else if(transVal_real>=1000){
				this.priority = NORMAL_PRIO;
			}
			else{
				this.priority = LOW_PRIO;
			}



		} catch (SQLException se) {
			log.error("Unexpected SQLException in NEW_ORDER");
			for (SQLException x = se; x != null; x = x.getNextException())
				log.error(x.getMessage());
			System.out.println(priority);
			se.printStackTrace();
			
			try {
				db.rollback();
				SQLString += "Abort;\",\n";
				db.stmtNewOrderInsertOrderLine.clearBatch();
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
		} catch (Exception e) {
			try {
				db.rollback();
				SQLString += "Abort;\",\n";
				db.stmtNewOrderInsertOrderLine.clearBatch();
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
			throw e;
		}
		/*
		 * log.info("Reached the point of creating one NEW_ORDER W_ID "+newOrder.
		 * w_id+" D_ID "+newOrder.d_id+" C_ID "+newOrder.c_id);
		 * System.exit(0);
		 */
	}

	private void traceNewOrder(Logger log, Formatter fmt[]) {
		fmt[0].format("                                    New Order");

		if (transEnd == 0) {
			// NEW_ORDER INPUT screen
			fmt[1].format("Warehouse: %6d  District: %2d                       Date:",
					newOrder.w_id, newOrder.d_id);
			fmt[2].format("Customer:    %4d  Name:                    Credit:      %%Disc:",
					newOrder.c_id);
			fmt[3].format("Order Number:            Number of Lines:           W_tax:         D_tax:");

			fmt[5].format("Supp_W   Item_Id  Item Name                  Qty  Stock  B/G  Price    Amount");

			for (int i = 0; i < 15; i++) {
				if (newOrder.ol_i_id[i] != 0)
					fmt[6 + i].format("%6d   %6d                              %2d",
							newOrder.ol_supply_w_id[i],
							newOrder.ol_i_id[i], newOrder.ol_quantity[i]);
				else
					fmt[6 + i].format("______   ______                              __");
			}

			fmt[21].format("Execution Status:                                             Total:  $");
		} else {
			// NEW_ORDER OUTPUT screen
			fmt[1].format("Warehouse: %6d  District: %2d                       Date: %19.19s",
					newOrder.w_id, newOrder.d_id, newOrder.o_entry_d);
			fmt[2].format("Customer:    %4d  Name: %-16.16s   Credit: %2.2s   %%Disc: %5.2f",
					newOrder.c_id, newOrder.c_last,
					newOrder.c_credit, newOrder.c_discount * 100.0);
			fmt[3].format("Order Number:  %8d  Number of Lines: %2d        W_tax: %5.2f   D_tax: %5.2f",
					newOrder.o_id, newOrder.o_ol_cnt,
					newOrder.w_tax * 100.0, newOrder.d_tax * 100.0);

			fmt[5].format("Supp_W   Item_Id  Item Name                  Qty  Stock  B/G  Price    Amount");

			for (int i = 0; i < 15; i++) {
				if (newOrder.ol_i_id[i] != 0)
					fmt[6 + i].format("%6d   %6d   %-24.24s   %2d    %3d    %1.1s   $%6.2f  $%7.2f",
							newOrder.ol_supply_w_id[i],
							newOrder.ol_i_id[i], newOrder.i_name[i],
							newOrder.ol_quantity[i],
							newOrder.s_quantity[i],
							newOrder.brand_generic[i],
							newOrder.i_price[i],
							newOrder.ol_amount[i]);
			}

			fmt[21].format("Execution Status: %-24.24s                    Total:  $%8.2f",
					newOrder.execution_status, newOrder.total_amount);
		}
	}

	private class NewOrderItem {
		/* terminal input data */
		public int i_id;
		public double i_price;
		public String i_name;
		public String i_data;
	}

	private class NewOrderData {
		/* terminal input data */
		public int w_id;
		public int d_id;
		public int c_id;

		public int ol_supply_w_id[] = new int[15];
		public int ol_i_id[] = new int[15];
		public int ol_quantity[] = new int[15];

		/* terminal output data */
		public String c_last;
		public String c_credit;
		public double c_discount;
		public double w_tax;
		public double d_tax;
		public int o_ol_cnt;
		public int o_id;
		public String o_entry_d;
		public double total_amount;
		public String execution_status;

		public String i_name[] = new String[15];
		public int s_quantity[] = new int[15];
		public String brand_generic[] = new String[15];
		public double i_price[] = new double[15];
		public double ol_amount[] = new double[15];
		public String dist_value[] = new String[15];
		public boolean found[] = new boolean[15];
	}

	/*
	 * **********************************************************************
	 * **********************************************************************
	 * ***** PAYMENT related methods and subclass. **************************
	 * **********************************************************************
	 *********************************************************************/
	public void generatePayment(Logger log, jTPCCRandom rnd, long due) {
		transType = TT_PAYMENT;
		transDue = due;
		transStart = 0;
		transEnd = 0;
		transRbk = false;
		transError = null;
		transGeneratime = System.currentTimeMillis();
		transVal_real = 0;

		newOrder = null;
		payment = new PaymentData();
		orderStatus = null;
		stockLevel = null;
		delivery = null;
		deliveryBG = null;

		// change 11.13
		// payment.w_id = terminalWarehouse; // 2.5.1.1
		// payment.d_id = rnd.nextInt(1, 10); // 2.5.1.2
		// payment.c_w_id = payment.w_id;
		// payment.c_d_id = payment.d_id;
		// if (rnd.nextInt(1, 100) > 85) {
		// 	payment.c_d_id = rnd.nextInt(1, 10);
		// 	while (payment.c_w_id == payment.w_id && numWarehouses > 1)
		// 		payment.c_w_id = rnd.nextInt(1, numWarehouses);
		// }
		// if (rnd.nextInt(1, 100) <= 60) {
		// 	payment.c_last = rnd.getCLast();
		// 	payment.c_id = 0;
		// } else {
		// 	payment.c_last = null;
		// 	payment.c_id = rnd.getCustomerID();
		// }

		// // 2.5.1.3
		// payment.h_amount = ((double) rnd.nextLong(100, 1000000)) / 100.0;
		// if (payment.h_amount<10000/3){
		// 	priority = LOW_PRIO;
		// }
		// else if (payment.h_amount>10000/3*2){
		// 	priority = HIGH_PRIO;
		// }
		// else{
		// 	priority = NORMAL_PRIO;
		// }
		payment.h_amount = 0;
	}

	public void executePayment(Logger log, jTPCCConnection db, jTPCCRandom rnd)
			throws Exception {
		PreparedStatement stmt;
		ResultSet rs;
		// Vector<Integer> c_id_list = new Vector<Integer>();
		Vector<Integer> o_id_list = new Vector<Integer>();
		Vector<Integer>	w_id_list = new Vector<Integer>();
		Vector<Integer>	d_id_list = new Vector<Integer>();
		int order_length = 0;
		int randIndex;

		long h_date = System.currentTimeMillis();
		
		int dbType;
		dbType = db.getdbtype();
		try {
		// 	if(with_prio == 1){
		// 		if(dbType == DB_COCKROACH){
		// 			switch (priority) {
		// 				case HIGH_PRIO:
		// 					stmt = db.stmtSetPriorityHigh;
		// 					break;
		// 				case NORMAL_PRIO:
		// 					stmt = db.stmtSetPriorityNormal;
		// 					break;
		// 				case LOW_PRIO:
		// 					stmt = db.stmtSetPriorityLow;
		// 					break;
		// 				default:
		// 					stmt = db.stmtSetPriorityLow;
		// 					break;
		// 			}
		// 			stmt.execute();
		// 		}
		// 	}

			//Select the unpay order
			stmt = db.stmtPaymentSelectNewOrder;
			
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }
			params.clear();
			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
					break;
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}


			// while (rs.next()) {
			// 	o_id_list.add(rs.getInt("no_o_id"));
			// 	w_id_list.add(rs.getInt("no_w_id"));
			// 	d_id_list.add(rs.getInt("no_d_id"));
			// 	order_length++;
			// }
			// if (order_length == 0) {
			// 	rs.close();
			// 	// throw new Exception("Unpay Order not found");
			// 	return;
			// }

			if (!rs.next()) {
				abort = 1;
				db.rollback();
				SQLString += "Abort;\",\n";
				return;
			}

			// randIndex = rnd.nextInt(0, order_length-1);
			payment.o_id = rs.getInt("no_o_id");
			payment.w_id = rs.getInt("no_w_id");
			payment.d_id = rs.getInt("no_d_id");
			rs.close();

			stmt = db.stmtPaymentUpdateNewOrder;
			stmt.setInt(1, payment.o_id);
			stmt.setInt(2, payment.w_id);
			stmt.setInt(3, payment.d_id);
			params.clear();
			params.add(payment.o_id);
			params.add(payment.w_id);
			params.add(payment.d_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }
			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt.executeUpdate();

			stmt = db.stmtPaymentSelectOorderData;
			stmt.setInt(1, payment.o_id);
			stmt.setInt(2, payment.w_id);
			stmt.setInt(3, payment.d_id);
			params.clear();
			params.add(payment.o_id);
			params.add(payment.w_id);
			params.add(payment.d_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			if (!rs.next()) {
				throw new Exception("Oorder.o_c_id for" +
						" W_ID=" + payment.c_w_id +
						" D_ID=" + payment.c_d_id +
						" O_ID=" + payment.o_id + " not found");
			}
			payment.c_id = rs.getInt("o_c_id");
			rs.close();




			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			stmt = db.stmtPaymentSelectOrderLineAmount;
			stmt.setInt(1, payment.o_id);
			stmt.setInt(2, payment.w_id);
			stmt.setInt(3, payment.d_id);
			params.clear();
			params.add(payment.o_id);
			params.add(payment.w_id);
			params.add(payment.d_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			while(rs.next()){
				payment.h_amount+=rs.getDouble("ol_amount");
			}
			
			double alpha = 0.9;
			transOrderVal = payment.h_amount;
			transVal_real = alpha*payment.h_amount;
			if(transVal_real>=30000){
				this.priority = EX_HIGH_PRIO;
			}
			else if(transVal_real>=5000){
				this.priority = HIGH_PRIO;
			}
			else if(transVal_real>=1000){
				this.priority = NORMAL_PRIO;
			}
			else{
				this.priority = LOW_PRIO;
			}
			
			// Update the DISTRICT.
			stmt = db.stmtPaymentUpdateDistrict;
			stmt.setDouble(1, payment.h_amount);
			stmt.setInt(2, payment.w_id);
			stmt.setInt(3, payment.d_id);
			params.clear();
			params.add(payment.h_amount);
			params.add(payment.w_id);
			params.add(payment.d_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt.executeUpdate();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			// Select the DISTRICT.
			stmt = db.stmtPaymentSelectDistrict;
			stmt.setInt(1, payment.w_id);
			stmt.setInt(2, payment.d_id);
			params.clear();
			params.add(payment.w_id);
			params.add(payment.d_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			if (!rs.next()) {
				rs.close();
				throw new Exception("District for" +
						" W_ID=" + payment.w_id +
						" D_ID=" + payment.d_id + " not found");
			}
			payment.d_name = rs.getString("d_name");
			payment.d_street_1 = rs.getString("d_street_1");
			payment.d_street_2 = rs.getString("d_street_2");
			payment.d_city = rs.getString("d_city");
			payment.d_state = rs.getString("d_state");
			payment.d_zip = rs.getString("d_zip");
			rs.close();

			// Update the WAREHOUSE.
			stmt = db.stmtPaymentUpdateWarehouse;
			stmt.setDouble(1, payment.h_amount);
			stmt.setInt(2, payment.w_id);
			params.clear();
			params.add(payment.h_amount);
			params.add(payment.w_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt.executeUpdate();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			// Select the WAREHOUSE.
			stmt = db.stmtPaymentSelectWarehouse;
			stmt.setInt(1, payment.w_id);
			params.clear();
			params.add(payment.w_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			if (!rs.next()) {
				rs.close();
				throw new Exception("Warehouse for" +
						" W_ID=" + payment.w_id + " not found");
			}
			payment.w_name = rs.getString("w_name");
			payment.w_street_1 = rs.getString("w_street_1");
			payment.w_street_2 = rs.getString("w_street_2");
			payment.w_city = rs.getString("w_city");
			payment.w_state = rs.getString("w_state");
			payment.w_zip = rs.getString("w_zip");
			rs.close();

			// If C_LAST is given instead of C_ID (60%), determine the C_ID.
			// if (payment.c_last != null) {
			// 	stmt = db.stmtPaymentSelectCustomerListByLast;
			// 	stmt.setInt(1, payment.c_w_id);
			// 	stmt.setInt(2, payment.c_d_id);
			// 	stmt.setString(3, payment.c_last);
			// 	rs = stmt.executeQuery();

			// 	if(timecounter == 1){
			// 		timeCounterNow = System.currentTimeMillis();
			// 		timeDelta = timeCounterNow - transStart;
			// 		if (timeDelta > 200) {
			// 			abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
			// 			if (abortPoss > 1) {
			// 				abortPoss = 1;
			// 			}
			// 			int poss = rnd.nextInt(1, 100);
			// 			if (poss < 100 * abortPoss) {
			// 				abort = 1;
			// 				db.rollback();
			// 				return;
			// 			}
			// 		}
			// 	}

			// 	while (rs.next())
			// 		c_id_list.add(rs.getInt("c_id"));
			// 	rs.close();

			// 	if (c_id_list.size() == 0) {
			// 		throw new Exception("Customer(s) for" +
			// 				" C_W_ID=" + payment.c_w_id +
			// 				" C_D_ID=" + payment.c_d_id +
			// 				" C_LAST=" + payment.c_last + " not found");
			// 	}

			// 	payment.c_id = c_id_list.get((c_id_list.size() + 1) / 2 - 1);
			// }

			
			// Select the CUSTOMER.
			payment.c_w_id = payment.w_id;
			payment.c_d_id = payment.d_id;
			stmt = db.stmtPaymentSelectCustomer;
			stmt.setInt(1, payment.c_w_id);
			stmt.setInt(2, payment.c_d_id);
			stmt.setInt(3, payment.c_id);
			params.clear();
			params.add(payment.c_w_id);
			params.add(payment.c_d_id);
			params.add(payment.c_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						rs.close();
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			if (!rs.next()) {
				throw new Exception("Customer for" +
						" C_W_ID=" + payment.c_w_id +
						" C_D_ID=" + payment.c_d_id +
						" C_ID=" + payment.c_id + " not found");
			}
			payment.c_first = rs.getString("c_first");
			payment.c_middle = rs.getString("c_middle");
			if (payment.c_last == null)
				payment.c_last = rs.getString("c_last");
			payment.c_street_1 = rs.getString("c_street_1");
			payment.c_street_2 = rs.getString("c_street_2");
			payment.c_city = rs.getString("c_city");
			payment.c_state = rs.getString("c_state");
			payment.c_zip = rs.getString("c_zip");
			payment.c_phone = rs.getString("c_phone");
			payment.c_since = rs.getTimestamp("c_since").toString();
			payment.c_credit = rs.getString("c_credit");
			payment.c_credit_lim = rs.getDouble("c_credit_lim");
			payment.c_discount = rs.getDouble("c_discount");
			payment.c_balance = rs.getDouble("c_balance");
			payment.c_data = new String("");
			rs.close();

			// Update the CUSTOMER.
			payment.c_balance -= payment.h_amount;
			if (payment.c_credit.equals("GC")) {
				// Customer with good credit, don't update C_DATA.
				transVal_real = transVal_real * 0.9;
				stmt = db.stmtPaymentUpdateCustomer;
				stmt.setDouble(1, payment.h_amount);
				stmt.setDouble(2, payment.h_amount);
				stmt.setInt(3, payment.c_w_id);
				stmt.setInt(4, payment.c_d_id);
				stmt.setInt(5, payment.c_id);
				params.clear();
				params.add(payment.h_amount);
				params.add(payment.h_amount);
				params.add(payment.c_w_id);
				params.add(payment.c_d_id);
				params.add(payment.c_id);

				// if(dbType == DB_MYSQL){
				// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
				// }
				// else{
				// 	SQLString += stmt.toString()+";\n";
				// }

				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}

				stmt.executeUpdate();

				if(timecounter == 1){
					timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - transStart;
					if (timeDelta > 200) {
						abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
						if (abortPoss > 1) {
							abortPoss = 1;
						}
						int poss = rnd.nextInt(1, 100);
						if (poss < 100 * abortPoss) {
							abort = 1;
							db.rollback();
							SQLString += "Abort;\",\n";
							return;
						}
					}
				}

			} else {
				// Customer with bad credit, need to do the C_DATA work.
				stmt = db.stmtPaymentSelectCustomerData;
				stmt.setInt(1, payment.c_w_id);
				stmt.setInt(2, payment.c_d_id);
				stmt.setInt(3, payment.c_id);
				params.clear();
				params.add(payment.c_w_id);
				params.add(payment.c_d_id);
				params.add(payment.c_id);

				// if(dbType == DB_MYSQL){
				// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
				// }
				// else{
				// 	SQLString += stmt.toString()+";\n";
				// }

				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}

				rs = stmt.executeQuery();

				if(timecounter == 1){
					timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - transStart;
					if (timeDelta > 200) {
						abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
						if (abortPoss > 1) {
							abortPoss = 1;
						}
						int poss = rnd.nextInt(1, 100);
						if (poss < 100 * abortPoss) {
							abort = 1;
							rs.close();
							db.rollback();
							SQLString += "Abort;\",\n";
							return;
						}
					}
				}

				if (!rs.next()) {
					throw new Exception("Customer.c_data for" +
							" C_W_ID=" + payment.c_w_id +
							" C_D_ID=" + payment.c_d_id +
							" C_ID=" + payment.c_id + " not found");
				}
				payment.c_data = rs.getString("c_data");
				rs.close();

				stmt = db.stmtPaymentUpdateCustomerWithData;
				params.clear();
				stmt.setDouble(1, payment.h_amount);
				params.add(payment.h_amount);
				stmt.setDouble(2, payment.h_amount);
				params.add(payment.h_amount);
				StringBuffer sbData = new StringBuffer();
				Formatter fmtData = new Formatter(sbData);
				fmtData.format("C_ID=%d C_D_ID=%d C_W_ID=%d " +
						"D_ID=%d W_ID=%d H_AMOUNT=%.2f   ",
						payment.c_id, payment.c_d_id, payment.c_w_id,
						payment.d_id, payment.w_id, payment.h_amount);
				sbData.append(payment.c_data);
				if (sbData.length() > 500)
					sbData.setLength(500);
				payment.c_data = sbData.toString();
				stmt.setString(3, payment.c_data);
				params.add(payment.c_data);
				stmt.setInt(4, payment.c_w_id);
				params.add(payment.c_w_id);
				stmt.setInt(5, payment.c_d_id);
				params.add(payment.c_d_id);
				stmt.setInt(6, payment.c_id);
				params.add(payment.c_id);

				// if(dbType == DB_MYSQL){
				// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
				// }
				// else{
				// 	SQLString += stmt.toString()+";\n";
				// }
				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}

				stmt.executeUpdate();

				if(timecounter == 1){
					timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - transStart;
					if (timeDelta > 200) {
						abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
						if (abortPoss > 1) {
							abortPoss = 1;
						}
						int poss = rnd.nextInt(1, 100);
						if (poss < 100 * abortPoss) {
							abort = 1;
							db.rollback();
							SQLString += "Abort;\",\n";
							return;
						}
					}
				}
			}

			// Insert the HISORY row.
			stmt = db.stmtPaymentInsertHistory;
			Timestamp time = new Timestamp(h_date);
			stmt.setInt(1, payment.c_id);
			stmt.setInt(2, payment.c_d_id);
			stmt.setInt(3, payment.c_w_id);
			stmt.setInt(4, payment.d_id);
			stmt.setInt(5, payment.w_id);
			stmt.setTimestamp(6, time);
			stmt.setDouble(7, payment.h_amount);
			stmt.setString(8, payment.w_name + "    " + payment.d_name);
			params.clear();
			params.add(payment.c_id);
			params.add(payment.c_d_id);
			params.add(payment.c_w_id);
			params.add(payment.d_id);
			params.add(payment.w_id);
			params.add(time);
			params.add(payment.h_amount);
			params.add(payment.w_name + "    " + payment.d_name);
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }
			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt.executeUpdate();

			if(timecounter == 1){
				timeCounterNow = System.currentTimeMillis();
				timeDelta = timeCounterNow - transStart;
				if (timeDelta > 200) {
					abortPoss = (timeDelta - 200) / (0.1 * 200) * 0.008;
					if (abortPoss > 1) {
						abortPoss = 1;
					}
					int poss = rnd.nextInt(1, 100);
					if (poss < 100 * abortPoss) {
						abort = 1;
						db.rollback();
						SQLString += "Abort;\",\n";
						return;
					}
				}
			}

			payment.h_date = new Timestamp(h_date).toString();

			if(value_loss){
				timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - transStart;
					if (timeDelta > 200) {
						double valloss = transVal_real*(timeDelta - 200) / (0.1 * 200) * 0.017;
						if(valloss<transVal_real){
							transVal_real = transVal_real-transVal_real*(timeDelta - 200) / (0.1 * 200) * 0.0;
						}
						else{
							transVal_real = 0;
						}
					}
			}
			
			db.commit();
			SQLString += "COMMIT;\",\n";
		} catch (SQLException se) {
			log.error("Unexpected SQLException in PAYMENT");
			for (SQLException x = se; x != null; x = x.getNextException())
				log.error(x.getMessage());
			se.printStackTrace();

			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
		} catch (Exception e) {
			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
			throw e;
		}
	}

	private void tracePayment(Logger log, Formatter fmt[]) {
		fmt[0].format("                                     Payment");

		if (transEnd == 0) {
			// PAYMENT INPUT screen
			fmt[1].format("Date: ");
			fmt[3].format("Warehouse: %6d                         District: %2d",
					payment.w_id, payment.d_id);

			if (payment.c_last == null) {
				fmt[8].format("Customer: %4d  Cust-Warehouse: %6d  Cust-District: %2d",
						payment.c_id, payment.c_w_id, payment.c_d_id);
				fmt[9].format("Name:                       ________________       Since:");
			} else {
				fmt[8].format("Customer: ____  Cust-Warehouse: %6d  Cust-District: %2d",
						payment.c_w_id, payment.c_d_id);
				fmt[9].format("Name:                       %-16.16s       Since:",
						payment.c_last);
			}
			fmt[10].format("                                                   Credit:");
			fmt[11].format("                                                   %%Disc:");
			fmt[12].format("                                                   Phone:");

			fmt[14].format("Amount Paid:          $%7.2f        New Cust-Balance:",
					payment.h_amount);
			fmt[15].format("Credit Limit:");
			fmt[17].format("Cust-Data:");
		} else {
			// PAYMENT OUTPUT screen
			fmt[1].format("Date: %-19.19s", payment.h_date);
			fmt[3].format("Warehouse: %6d                         District: %2d",
					payment.w_id, payment.d_id);
			fmt[4].format("%-20.20s                      %-20.20s",
					payment.w_street_1, payment.d_street_1);
			fmt[5].format("%-20.20s                      %-20.20s",
					payment.w_street_2, payment.d_street_2);
			fmt[6].format("%-20.20s %2.2s %5.5s-%4.4s        %-20.20s %2.2s %5.5s-%4.4s",
					payment.w_city, payment.w_state,
					payment.w_zip.substring(0, 5), payment.w_zip.substring(5, 9),
					payment.d_city, payment.d_state,
					payment.d_zip.substring(0, 5), payment.d_zip.substring(5, 9));
			log.trace("w_zip=" + payment.w_zip + " d_zip=" + payment.d_zip);

			fmt[8].format("Customer: %4d  Cust-Warehouse: %6d  Cust-District: %2d",
					payment.c_id, payment.c_w_id, payment.c_d_id);
			fmt[9].format("Name:   %-16.16s %2.2s %-16.16s       Since:  %-10.10s",
					payment.c_first, payment.c_middle, payment.c_last,
					payment.c_since);
			fmt[10].format("        %-20.20s                       Credit: %2s",
					payment.c_street_1, payment.c_credit);
			fmt[11].format("        %-20.20s                       %%Disc:  %5.2f",
					payment.c_street_2, payment.c_discount * 100.0);
			fmt[12].format("        %-20.20s %2.2s %5.5s-%4.4s         Phone:  %6.6s-%3.3s-%3.3s-%4.4s",
					payment.c_city, payment.c_state,
					payment.c_zip.substring(0, 5), payment.c_zip.substring(5, 9),
					payment.c_phone.substring(0, 6), payment.c_phone.substring(6, 9),
					payment.c_phone.substring(9, 12), payment.c_phone.substring(12, 16));

			fmt[14].format("Amount Paid:          $%7.2f        New Cust-Balance: $%14.2f",
					payment.h_amount, payment.c_balance);
			fmt[15].format("Credit Limit:   $%13.2f", payment.c_credit_lim);
			if (payment.c_data.length() >= 200) {
				fmt[17].format("Cust-Data: %-50.50s", payment.c_data.substring(0, 50));
				fmt[18].format("           %-50.50s", payment.c_data.substring(50, 100));
				fmt[19].format("           %-50.50s", payment.c_data.substring(100, 150));
				fmt[20].format("           %-50.50s", payment.c_data.substring(150, 200));
			} else {
				fmt[17].format("Cust-Data:");
			}
		}
	}

	private class PaymentData {
		/* terminal input data */
		public int w_id;
		public int d_id;
		public int c_id;
		public int c_d_id;
		public int c_w_id;
		public String c_last;
		public double h_amount;

		/* terminal output data */
		public String w_name;
		public String w_street_1;
		public String w_street_2;
		public String w_city;
		public String w_state;
		public String w_zip;
		public String d_name;
		public String d_street_1;
		public String d_street_2;
		public String d_city;
		public String d_state;
		public String d_zip;
		public String c_first;
		public String c_middle;
		public String c_street_1;
		public String c_street_2;
		public String c_city;
		public String c_state;
		public String c_zip;
		public String c_phone;
		public String c_since;
		public String c_credit;
		public double c_credit_lim;
		public double c_discount;
		public double c_balance;
		public String c_data;
		public String h_date;
		public int o_id;
		public int o_ol_cnt;
	}

	/*
	 * **********************************************************************
	 * **********************************************************************
	 * ***** ORDER_STATUS related methods and subclass. *********************
	 * **********************************************************************
	 *********************************************************************/
	public void generateOrderStatus(Logger log, jTPCCRandom rnd, long due) {
		transType = TT_ORDER_STATUS;
		transDue = due;
		transStart = 0;
		transEnd = 0;
		transRbk = false;
		transError = null;
		transGeneratime = System.currentTimeMillis();
		transVal_real = 1;

		newOrder = null;
		payment = null;
		orderStatus = new OrderStatusData();
		stockLevel = null;
		delivery = null;
		deliveryBG = null;

		orderStatus.w_id = terminalWarehouse;
		orderStatus.d_id = rnd.nextInt(1, 10);
		if (rnd.nextInt(1, 100) <= 60) {
			orderStatus.c_id = 0;
			orderStatus.c_last = rnd.getCLast();
		} else {
			orderStatus.c_id = rnd.getCustomerID();
			orderStatus.c_last = null;
		}
	}

	public void executeOrderStatus(Logger log, jTPCCConnection db)
			throws Exception {
		PreparedStatement stmt;
		ResultSet rs;
		Vector<Integer> c_id_list = new Vector<Integer>();
		int ol_idx = 0;
		int dbType;
		dbType = db.getdbtype();
		transVal_real = 0;
		inputdata = String.valueOf(orderStatus.w_id)+","
					+String.valueOf(orderStatus.d_id)+","
					+String.valueOf(orderStatus.c_id)+","
					+orderStatus.c_last;

		try {
			if(with_prio == 1){
				if(dbType == DB_COCKROACH){
					stmt = db.stmtSetPriorityLow;
					stmt.execute();
				}
			}

			// If C_LAST is given instead of C_ID (60%), determine the C_ID.
			if (orderStatus.c_last != null) {
				stmt = db.stmtOrderStatusSelectCustomerListByLast;
				stmt.setInt(1, orderStatus.w_id);
				stmt.setInt(2, orderStatus.d_id);
				stmt.setString(3, orderStatus.c_last);
				params.clear();
				params.add(orderStatus.w_id);
				params.add(orderStatus.d_id);
				params.add(orderStatus.c_last);

				// if(dbType == DB_MYSQL){
				// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
				// }
				// else{
				// 	SQLString += stmt.toString()+";\n";
				// }
				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}

				rs = stmt.executeQuery();
				while (rs.next())
					c_id_list.add(rs.getInt("c_id"));
				rs.close();

				if (c_id_list.size() == 0) {
					throw new Exception("Customer(s) for" +
							" C_W_ID=" + orderStatus.w_id +
							" C_D_ID=" + orderStatus.d_id +
							" C_LAST=" + orderStatus.c_last + " not found");
				}

				orderStatus.c_id = c_id_list.get((c_id_list.size() + 1) / 2 - 1);
			}

			// Select the CUSTOMER.
			stmt = db.stmtOrderStatusSelectCustomer;
			stmt.setInt(1, orderStatus.w_id);
			stmt.setInt(2, orderStatus.d_id);
			stmt.setInt(3, orderStatus.c_id);
			params.clear();
			params.add(orderStatus.w_id);
			params.add(orderStatus.d_id);
			params.add(orderStatus.c_id);
			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt.executeQuery();
			if (!rs.next()) {
				throw new Exception("Customer for" +
						" C_W_ID=" + orderStatus.w_id +
						" C_D_ID=" + orderStatus.d_id +
						" C_ID=" + orderStatus.c_id + " not found");
			}
			orderStatus.c_first = rs.getString("c_first");
			orderStatus.c_middle = rs.getString("c_middle");
			if (orderStatus.c_last == null)
				orderStatus.c_last = rs.getString("c_last");
			orderStatus.c_balance = rs.getDouble("c_balance");
			rs.close();

			// Select the last ORDER for this customer.
			stmt = db.stmtOrderStatusSelectLastOrder;
			stmt.setInt(1, orderStatus.w_id);
			stmt.setInt(2, orderStatus.d_id);
			stmt.setInt(3, orderStatus.c_id);

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt.toString()+";\n";
			// }
			params.clear();
			params.add(orderStatus.w_id);
			params.add(orderStatus.d_id);
			params.add(orderStatus.c_id);

			switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
			}

			rs = stmt.executeQuery();
			if (!rs.next()) {
				throw new Exception("Last Order for" +
						" W_ID=" + orderStatus.w_id +
						" D_ID=" + orderStatus.d_id +
						" C_ID=" + orderStatus.c_id + " not found");
			}
			orderStatus.o_id = rs.getInt("o_id");
			orderStatus.o_entry_d = rs.getTimestamp("o_entry_d").toString();
			orderStatus.o_carrier_id = rs.getInt("o_carrier_id");
			if (rs.wasNull())
				orderStatus.o_carrier_id = -1;
			rs.close();

			stmt = db.stmtOrderStatusSelectOrderLine;
			stmt.setInt(1, orderStatus.w_id);
			stmt.setInt(2, orderStatus.d_id);
			stmt.setInt(3, orderStatus.o_id);
			params.clear();
			params.add(orderStatus.w_id);
			params.add(orderStatus.d_id);
			params.add(orderStatus.o_id);
			switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
			}

			rs = stmt.executeQuery();
			while (rs.next()) {
				Timestamp ol_delivery_d;

				orderStatus.ol_i_id[ol_idx] = rs.getInt("ol_i_id");
				orderStatus.ol_supply_w_id[ol_idx] = rs.getInt("ol_supply_w_id");
				orderStatus.ol_quantity[ol_idx] = rs.getInt("ol_quantity");
				orderStatus.ol_amount[ol_idx] = rs.getDouble("ol_amount");
				transVal_real += orderStatus.ol_amount[ol_idx];
				ol_delivery_d = rs.getTimestamp("ol_delivery_d");
				if (ol_delivery_d != null)
					orderStatus.ol_delivery_d[ol_idx] = ol_delivery_d.toString();
				else
					orderStatus.ol_delivery_d[ol_idx] = null;
				ol_idx++;
			}
			rs.close();

			while (ol_idx < 15) {
				orderStatus.ol_i_id[ol_idx] = 0;
				orderStatus.ol_supply_w_id[ol_idx] = 0;
				orderStatus.ol_quantity[ol_idx] = 0;
				orderStatus.ol_amount[ol_idx] = 0.0;
				orderStatus.ol_delivery_d[ol_idx] = null;
				ol_idx++;
			}
			double alpha = 0.2;
			transVal_real = alpha*transVal_real;
			db.commit();
			SQLString += "Commit;\",\n";
			if(transVal_real>=30000){
				this.priority = EX_HIGH_PRIO;
			}
			else if(transVal_real>=5000){
				this.priority = HIGH_PRIO;
			}
			else if(transVal_real>=1000){
				this.priority = NORMAL_PRIO;
			}
			else{
				this.priority = LOW_PRIO;
			}
		} catch (SQLException se) {
			log.error("Unexpected SQLException in ORDER_STATUS");
			for (SQLException x = se; x != null; x = x.getNextException())
				log.error(x.getMessage());
			se.printStackTrace();

			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
		} catch (Exception e) {
			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
			throw e;
		}
	}

	private void traceOrderStatus(Logger log, Formatter fmt[]) {
		fmt[0].format("                                  Order Status");

		if (transEnd == 0) {
			// ORDER_STATUS INPUT screen
			fmt[1].format("Warehouse: %6d   District: %2d",
					orderStatus.w_id, orderStatus.d_id);
			if (orderStatus.c_last == null)
				fmt[2].format("Customer: %4d   Name:                     ________________",
						orderStatus.c_id);
			else
				fmt[2].format("Customer: ____   Name:                     %-16.16s",
						orderStatus.c_last);
			fmt[3].format("Cust-Balance:");

			fmt[5].format("Order-Number:            Entry-Date:                       Carrier-Number:");
			fmt[6].format("Suppy-W      Item-Id     Qty    Amount        Delivery-Date");
		} else {
			// ORDER_STATUS OUTPUT screen
			fmt[1].format("Warehouse: %6d   District: %2d",
					orderStatus.w_id, orderStatus.d_id);
			fmt[2].format("Customer: %4d   Name: %-16.16s %2.2s %-16.16s",
					orderStatus.c_id, orderStatus.c_first,
					orderStatus.c_middle, orderStatus.c_last);
			fmt[3].format("Cust-Balance: $%13.2f", orderStatus.c_balance);

			if (orderStatus.o_carrier_id >= 0)
				fmt[5].format("Order-Number: %8d   Entry-Date: %-19.19s   Carrier-Number: %2d",
						orderStatus.o_id, orderStatus.o_entry_d, orderStatus.o_carrier_id);
			else
				fmt[5].format("Order-Number: %8d   Entry-Date: %-19.19s   Carrier-Number:",
						orderStatus.o_id, orderStatus.o_entry_d);
			fmt[6].format("Suppy-W      Item-Id     Qty    Amount        Delivery-Date");
			for (int i = 0; i < 15 && orderStatus.ol_i_id[i] > 0; i++) {
				fmt[7 + i].format(" %6d      %6d     %3d     $%8.2f     %-10.10s",
						orderStatus.ol_supply_w_id[i],
						orderStatus.ol_i_id[i],
						orderStatus.ol_quantity[i],
						orderStatus.ol_amount[i],
						(orderStatus.ol_delivery_d[i] == null) ? "" : orderStatus.ol_delivery_d[i]);
			}
		}
	}

	private class OrderStatusData {
		/* terminal input data */
		public int w_id;
		public int d_id;
		public int c_id;
		public String c_last;

		/* terminal output data */
		public String c_first;
		public String c_middle;
		public double c_balance;
		public int o_id;
		public String o_entry_d;
		public int o_carrier_id;

		public int ol_supply_w_id[] = new int[15];
		public int ol_i_id[] = new int[15];
		public int ol_quantity[] = new int[15];
		public double ol_amount[] = new double[15];
		public String ol_delivery_d[] = new String[15];
	}

	/*
	 * **********************************************************************
	 * **********************************************************************
	 * ***** STOCK_LEVEL related methods and subclass. **********************
	 * **********************************************************************
	 *********************************************************************/
	public void generateStockLevel(Logger log, jTPCCRandom rnd, long due,Map<Integer,Double> reStock) {
		transType = TT_STOCK_LEVEL;
		transDue = due;
		transStart = 0;
		transEnd = 0;
		transRbk = false;
		transError = null;
		transGeneratime = System.currentTimeMillis();
		transVal_real = 2100;

		newOrder = null;
		payment = null;
		orderStatus = null;
		stockLevel = new StockLevelData();
		delivery = null;
		deliveryBG = null;

		stockLevel.w_id = terminalWarehouse;
		stockLevel.d_id = terminalDistrict;
		stockLevel.threshold = rnd.nextInt(10, 20);
		stockLevel.reStock = reStock;
	}

	public void executeStockLevel(Logger log, jTPCCConnection db)
			throws Exception {
		PreparedStatement stmt;
		ResultSet rs;
		transVal_real = 0;
		inputdata = String.valueOf(stockLevel.w_id) + ","
					+ String.valueOf(stockLevel.d_id);
		int dbType = db.getdbtype();
		try {
			if(with_prio == 1){
				if(dbType == DB_COCKROACH){
					stmt = db.stmtSetPriorityLow;

					if(dbType == DB_MYSQL){
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
					}
					else{
						SQLString += stmt.toString()+";\n";
					}

					stmt.execute();
				}
			}
			int k = 0;
			if(stockLevel.reStock.isEmpty()||stockLevel.reStock == null){//管理员触发补货事务
				stmt = db.stmtStockLevelSelectOrder;
				stmt.setInt(1, stockLevel.w_id);
				stmt.setInt(2, stockLevel.d_id);
				rs = stmt.executeQuery();

				if(!rs.next()){
					rs.close();
					throw new SQLException("d_next_o_id for" +
						" W_ID=" + stockLevel.w_id +
						" D_ID=" + stockLevel.d_id +" not found");
				}
				stockLevel.d_next_o_id = rs.getInt("d_next_o_id");
				

				stmt = db.stmtStockLevelSelectStockDetail;
				stmt.setInt(1, stockLevel.w_id);
				stmt.setInt(2, stockLevel.d_id);
				stmt.setInt(3, stockLevel.d_next_o_id);
				rs = stmt.executeQuery();
				boolean isContainHotItem = false;
				while(rs.next()){
					if(k >= 50){//一次最多补50种货物
						break;
					}
					stockLevel.s_i_id[k++] = rs.getInt("s_i_id");
					stockLevel.ol_amount = rs.getDouble("ol_amount");
					stockLevel.ol_quantity = rs.getInt("ol_quantity");
					double price = (stockLevel.ol_amount / stockLevel.ol_quantity);//计算货物单价
					transVal_real += price;
					// if(parent.parent.isHotItem(stockLevel.s_i_id[k-1])){
					// 	isContainHotItem = true;
					// }
					if(VPSServer.isHotItem(stockLevel.s_i_id[k-1])){
						isContainHotItem = true;
					}
				}
				if(k == 0){
					rs.close();
					throw new SQLException("No Item Need to ");
				}
				double alpha = 0.9;
				if(isContainHotItem){
					alpha = 0.7;
					transVal_real = transVal_real*alpha;//大致测量该事务价值
				}
				else{
					alpha = 0.5;
					transVal_real = transVal_real*alpha;
				}



			}else{//new-order缺货导致补货发生
				for(Map.Entry<Integer, Double> entry : stockLevel.reStock.entrySet()){
					stockLevel.s_i_id[k++] = entry.getKey();
					transVal_real += entry.getValue()*5;
				}
				transVal_real = transVal_real/k*10;//触发型补货事务价值应该更高，权重设置为10

				
			}

			stmt = db.stmtStockLevelUpateStock;//排序防止死锁。
			Arrays.sort(stockLevel.s_i_id,0,k);
			for(int i = 0;i<k;i++){
				stmt.setInt(1, stockLevel.w_id);
				stmt.setInt(2,stockLevel.s_i_id[i]);
				params.clear();
				params.add(stockLevel.w_id);
				params.add(stockLevel.s_i_id[i]);
				stmt.addBatch();
				// if(dbType == DB_MYSQL){
				// 	SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
				// }
				// else{
				// 	SQLString += stmt.toString()+";\n";
				// }

				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}
			}

			stmt.executeBatch();

			stmt.clearBatch();

			if(value_loss){
				
				timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - transStart;
					if (timeDelta > 200) {//executeStockLevel 可容忍等待时间为200ms
						double valloss = transVal_real*(timeDelta - 200) / (0.1 * 200) * 0.008;
						if(valloss<transVal_real){
							transVal_real = transVal_real-transVal_real*(timeDelta - 200) / (0.1 * 200) * 0.008;
						}
						else{
							transVal_real = 0;
						}
					}
			}


			db.commit();
			SQLString += "Commit;\",\n";
			if(transVal_real>=30000){
				this.priority = EX_HIGH_PRIO;
			}
			else if(transVal_real>=5000){
				this.priority = HIGH_PRIO;
			}
			else if(transVal_real>=1000){
				this.priority = NORMAL_PRIO;
			}
			else{
				this.priority = LOW_PRIO;
			}
		} catch (SQLException se) {
			log.error("Unexpected SQLException in STOCK_LEVEL");
			for (SQLException x = se; x != null; x = x.getNextException())
				log.error(x.getMessage());
			se.printStackTrace();

			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
		} catch (Exception e) {
			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
			throw e;
		}
	}

	private void traceStockLevel(Logger log, Formatter fmt[]) {
		fmt[0].format("                                  Stock-Level");

		fmt[1].format("Warehouse: %6d   District: %2d",
				stockLevel.w_id, stockLevel.d_id);
		fmt[3].format("Stock Level Threshold: %2d",
				stockLevel.threshold);

		if (transEnd == 0)
			fmt[5].format("Low Stock:");
		else
			fmt[5].format("Low Stock: %3d",
					stockLevel.low_stock);
	}

	private class StockLevelData {
		/* terminal input data */
		public int w_id;
		public int d_id;
		public int threshold;
		public int d_next_o_id;
		public double ol_amount;
		public int ol_quantity;
		public int[] s_i_id = new int[101]; 
		public Map<Integer,Double>reStock;

		/* terminal output data */
		public int low_stock;
	}

	/*
	 * **********************************************************************
	 * **********************************************************************
	 * ***** DELIVERY related methods and subclass. *************************
	 * **********************************************************************
	 *********************************************************************/
	public void generateDelivery(Logger log, jTPCCRandom rnd, long due) {
		transType = TT_DELIVERY;
		transDue = due;
		transStart = 0;
		transEnd = 0;
		transRbk = false;
		transError = null;
		transGeneratime = System.currentTimeMillis();
		transVal_real = 0;

		newOrder = null;
		payment = null;
		orderStatus = null;
		stockLevel = null;
		delivery = new DeliveryData();
		deliveryBG = null;

		delivery.w_id = terminalWarehouse;
		delivery.o_carrier_id = rnd.nextInt(1, 10);
		delivery.execution_status = null;
		delivery.deliveryBG = null;
		transVal_real = 0;
	}

	public void executeDelivery(Logger log, jTPCCConnection db) {
		long now = System.currentTimeMillis();

		/*
		 * The DELIVERY transaction is different from all the others.
		 * The foreground transaction, experienced by the user, does
		 * not perform any interaction with the database. It only queues
		 * a request to perform such a transaction in the background
		 * (DeliveryBG). We store that TData object in the delivery
		 * part for the caller to pick up and queue/execute.
		 */
		// delivery.deliveryBG = new jTPCCTData(this.parent);
		delivery.deliveryBG = new jTPCCTData();
		delivery.deliveryBG.generateDeliveryBG(delivery.w_id, now,
				new Timestamp(now).toString(), this);
		delivery.execution_status = new String("Delivery has been queued");
	}

	private void traceDelivery(Logger log, Formatter fmt[]) {
		fmt[0].format("                                     Delivery");
		fmt[1].format("Warehouse: %6d", delivery.w_id);
		fmt[3].format("Carrier Number: %2d", delivery.o_carrier_id);
		if (transEnd == 0) {
			fmt[5].format("Execution Status: ");
		} else {
			fmt[5].format("Execution Status: %s", delivery.execution_status);
		}
	}

	public jTPCCTData getDeliveryBG()
			throws Exception {
		if (transType != TT_DELIVERY)
			throw new Exception("Not a DELIVERY");
		if (delivery.deliveryBG == null)
			throw new Exception("DELIVERY foreground not executed yet " +
					"or background part already consumed");

		jTPCCTData result = delivery.deliveryBG;
		delivery.deliveryBG = null;
		return result;
	}

	private class DeliveryData {
		/* terminal input data */
		public int w_id;
		public int o_carrier_id;

		/* terminal output data */
		public String execution_status;

		/*
		 * executeDelivery() will store the background request
		 * here for the caller to pick up and process as needed.
		 */
		public jTPCCTData deliveryBG;
	}

	/*
	 * **********************************************************************
	 * **********************************************************************
	 * ***** DELIVERY_BG related methods and subclass. **********************
	 * **********************************************************************
	 *********************************************************************/
	private void generateDeliveryBG(int w_id, long due, String ol_delivery_d,
			jTPCCTData parent) {
		/*
		 * The DELIVERY_BG part is created as a result of executing the
		 * foreground part of the DELIVERY transaction. Because of that
		 * it inherits certain information from it.
		 */
		numWarehouses = parent.numWarehouses;
		terminalWarehouse = parent.terminalWarehouse;
		terminalDistrict = parent.terminalDistrict;

		transType = TT_DELIVERY_BG;
		transDue = due;
		transStart = 0;
		transEnd = 0;
		transRbk = false;
		transError = null;
		transVal_real = 0;
		transGeneratime = System.currentTimeMillis();

		newOrder = null;
		payment = null;
		orderStatus = null;
		stockLevel = null;
		delivery = null;
		deliveryBG = new DeliveryBGData();

		deliveryBG.w_id = parent.delivery.w_id;
		deliveryBG.o_carrier_id = parent.delivery.o_carrier_id;
		deliveryBG.ol_delivery_d = ol_delivery_d;

		deliveryBG.delivered_o_id = new int[10];
		deliveryBG.delivered_c_id = new int[10];
		deliveryBG.sum_ol_amount = new double[10];
		for (int i = 0; i < 10; i++) {
			deliveryBG.delivered_o_id[i] = -1;
			deliveryBG.sum_ol_amount[i] = 0;
			deliveryBG.delivered_c_id[i] = -1;
		}
	}

	public void executeDeliveryBG(Logger log, jTPCCConnection db)
			throws Exception {
		PreparedStatement stmt1;
		PreparedStatement stmt2;
		ResultSet rs;
		int rc;
		int d_id;
		int o_id;
		int c_id;
		double sum_ol_amount;
		long now = System.currentTimeMillis();
		transStart = System.currentTimeMillis();
		int dbType = db.getdbtype();
		inputdata = String.valueOf(deliveryBG.w_id)+","
					+ "0,0";
		try {
			if(with_prio == 1){
				if(dbType == DB_COCKROACH){
					stmt1 = db.stmtSetPriorityLow;

					SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";

					stmt1.execute();
				}
			}
			for (d_id = 1; d_id <= 10; d_id++) {
				o_id = -1;

				// stmt1 = db.stmtDeliveryBGSelectOldestNewOrder;

				/*
				 * Try to find the oldest undelivered order for this
				 * DISTRICT. There may not be one, which is a case
				 * that needs to be reportd.
				 */
				while (o_id < 0) {
					stmt1 = db.stmtDeliveryBGSelectOldestNewOrder;
					stmt1.setInt(1, deliveryBG.w_id);
					stmt1.setInt(2, d_id);
					params.clear();
					params.add(deliveryBG.w_id);
					params.add(d_id);

					// if(dbType == DB_MYSQL){
					// 	SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
					// }
					// else{
					// 	SQLString += stmt1.toString()+";\n";
					// }

					switch (dbType) {
						case DB_MYSQL:
							SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
							break;
						case DB_POSTGRES:
							SQLString += stmt1.toString()+";\n";
							break;
						case DB_ORACLE:
							String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
							sqlTemplate = buildDebugSql(sqlTemplate,params);
							SQLString += sqlTemplate+";\n";
						default:
							break;
					}

					rs = stmt1.executeQuery();
					if (!rs.next()) {
						rs.close();
						break;
					}
						o_id = rs.getInt("no_o_id");
					
					rs.close();
					if(dbType == DB_ORACLE){
						stmt1 = db.stmtDeliveryBGLockOldestNewOrder;
						stmt1.setInt(1, o_id);
						stmt1.setInt(2, deliveryBG.w_id);
						stmt1.setInt(3, d_id);
						params.clear();
						params.add(o_id);
						params.add(deliveryBG.w_id);
						params.add(d_id);

						String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";


						rs = stmt1.executeQuery();
						if (!rs.next()) {
							rs.close();
							break;
						}
							o_id = rs.getInt("no_o_id");
						
						rs.close();
					}
					/*
					 * This logic only works in SNAPSHOT isolation
					 * level. Because we select new_order for update,
					 * the order must be not selected by other termnial
					 * as long as this SQL statement return it.
					 */
				}

				if (o_id < 0) {
					// No undelivered NEW_ORDER found for this DISTRICT.
					continue;
				}
				deliveryBG.delivered_o_id[d_id - 1] = o_id;
			}
			stmt1 = db.stmtDeliveryBGDeleteOldestNewOrder;
			params.clear();
			for (d_id = 1; d_id <= 10; d_id++) {
				stmt1.setInt(d_id * 3 - 2, deliveryBG.w_id);
				stmt1.setInt(d_id * 3 - 1, d_id);
				stmt1.setInt(d_id * 3, deliveryBG.delivered_o_id[d_id - 1]);
				params.add(deliveryBG.w_id);
				params.add(d_id);
				params.add(deliveryBG.delivered_o_id[d_id - 1]);
			}

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt1.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}
			

			stmt1.executeUpdate();

			/*
			 * We found out oldest undelivered order for this DISTRICT
			 * and the NEW_ORDER line has been deleted. Process the
			 * rest of the DELIVERY_BG.
			 */

			// Update the ORDER setting the o_carrier_id.
			stmt1 = db.stmtDeliveryBGUpdateOrder;
			params.clear();
			stmt1.setInt(1, deliveryBG.o_carrier_id);
			params.add(deliveryBG.o_carrier_id);
			for (d_id = 1; d_id <= 10; d_id++) {
				stmt1.setInt(d_id * 3 - 1, deliveryBG.w_id);
				stmt1.setInt(d_id * 3, d_id);
				stmt1.setInt(d_id * 3 + 1, deliveryBG.delivered_o_id[d_id - 1]);
				params.add(deliveryBG.w_id);
				params.add(d_id);
				params.add(deliveryBG.delivered_o_id[d_id - 1]);
			}

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt1.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt1.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt1.executeUpdate();

			// Get the o_c_id from the ORDER.
			stmt1 = db.stmtDeliveryBGSelectOrder;
			params.clear();
			for (d_id = 1; d_id <= 10; d_id++) {
				stmt1.setInt(d_id * 3 - 2, deliveryBG.w_id);
				stmt1.setInt(d_id * 3 - 1, d_id);
				stmt1.setInt(d_id * 3, deliveryBG.delivered_o_id[d_id - 1]);
				params.add(deliveryBG.w_id);
				params.add(d_id);
				params.add(deliveryBG.delivered_o_id[d_id - 1]);
			}

			// if(dbType == DB_MYSQL){
			// 	SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
			// }
			// else{
			// 	SQLString += stmt1.toString()+";\n";
			// }

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt1.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt1.executeQuery();
			while (rs.next()) {
				d_id = rs.getInt("o_d_id");
				c_id = rs.getInt("o_c_id");
				deliveryBG.delivered_c_id[d_id - 1] = c_id;
			}
			rs.close();
			for (d_id = 1; d_id <= 10; d_id++) {
				o_id = deliveryBG.delivered_o_id[d_id - 1];
				if (o_id >= 0 && deliveryBG.delivered_c_id[d_id - 1] < 0) {
					throw new Exception("ORDER in DELIVERY_BG for" +
							" O_W_ID=" + deliveryBG.w_id +
							" O_D_ID=" + d_id +
							" O_ID=" + o_id + " not found");
				}
			}

			// Update ORDER_LINE setting the ol_delivery_d.
			stmt1 = db.stmtDeliveryBGUpdateOrderLine;
			params.clear();
			Timestamp time_now = new Timestamp(now);
			stmt1.setTimestamp(1, time_now);
			params.add(time_now);
			for (d_id = 1; d_id <= 10; d_id++) {
				stmt1.setInt(d_id * 3 - 1, deliveryBG.w_id);
				stmt1.setInt(d_id * 3, d_id);
				stmt1.setInt(d_id * 3 + 1, deliveryBG.delivered_o_id[d_id - 1]);
				params.add(deliveryBG.w_id);
				params.add(d_id);
				params.add(deliveryBG.delivered_o_id[d_id - 1]);
			}

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt1.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			stmt1.executeUpdate();

			// Select the sum(ol_amount) from ORDER_LINE.

			stmt1 = db.stmtDeliveryBGSelectSumOLAmount;
			params.clear();
			for (d_id = 1; d_id <= 10; d_id++) {
				stmt1.setInt(d_id * 3 - 2, deliveryBG.w_id);
				stmt1.setInt(d_id * 3 - 1, d_id);
				stmt1.setInt(d_id * 3, deliveryBG.delivered_o_id[d_id - 1]);
				params.add(deliveryBG.w_id);
				params.add(d_id);
				params.add(deliveryBG.delivered_o_id[d_id - 1]);
			}

			switch (dbType) {
				case DB_MYSQL:
					SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
					break;
				case DB_POSTGRES:
					SQLString += stmt1.toString()+";\n";
					break;
				case DB_ORACLE:
					String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
					sqlTemplate = buildDebugSql(sqlTemplate,params);
					SQLString += sqlTemplate+";\n";
				default:
					break;
			}

			rs = stmt1.executeQuery();
			int k = 0;
			while (rs.next()) {
				d_id = rs.getInt("ol_d_id");
				deliveryBG.sum_ol_amount[d_id - 1] = rs.getDouble("sum_ol_amount");
				transVal_real += deliveryBG.sum_ol_amount[d_id - 1];
				k++;
			}
			transVal_real = transVal_real/k;
			transOrderVal = transVal_real;
			DeliveryOrderCount = k;
			transVal_real = transVal_real *0.5;//快递事务权重为0.5
			rs.close();

			// Update the CUSTOMER.
			for (d_id = 1; d_id <= 10; d_id++) {
				o_id = deliveryBG.delivered_o_id[d_id - 1];
				if (o_id < 0) {
					continue;
				}
				double ans = deliveryBG.sum_ol_amount[d_id - 1];
				// if (ans < 0) {
				// 	throw new Exception("sum(OL_AMOUNT) for ORDER_LINEs with " +
				// 			" OL_W_ID=" + deliveryBG.w_id +
				// 			" OL_D_ID=" + d_id +
				// 			" OL_O_ID=" + o_id + " not found");
				// }
				c_id = deliveryBG.delivered_c_id[d_id - 1];
				stmt1 = db.stmtDeliveryBGUpdateCustomer;
				params.clear();
				stmt1.setInt(1, deliveryBG.w_id);
				stmt1.setInt(2, d_id);
				stmt1.setInt(3, c_id);
				params.add(deliveryBG.w_id);
				params.add(d_id);
				params.add(c_id);
				switch (dbType) {
					case DB_MYSQL:
						SQLString += stmt1.toString().replaceFirst("^\\S+\\s", "")+";\n";
						break;
					case DB_POSTGRES:
						SQLString += stmt1.toString()+";\n";
						break;
					case DB_ORACLE:
						String sqlTemplate = stmt1.unwrap(oracle.jdbc.internal.OraclePreparedStatement.class).getOriginalSql();
						sqlTemplate = buildDebugSql(sqlTemplate,params);
						SQLString += sqlTemplate+";\n";
					default:
						break;
				}

				stmt1.executeUpdate();
				// Recored the delivered O_ID in the DELIVERY_BG
			}

			if(value_loss){
				
				timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - transStart;
					if (timeDelta > 3000) {//delivery 可容忍等待时间为3s
						double valloss = transVal_real*(timeDelta - 3000) / (0.1 * 3000) * 0.008;
						if(valloss<transVal_real){
							transVal_real = transVal_real-transVal_real*(timeDelta - 3000) / (0.1 * 3000) * 0.008;
						}
						else{
							transVal_real = 0;
						}
					}
			}

			db.commit();
			SQLString += "COMMIT;\",\n";
			if(transVal_real>=30000){
				this.priority = EX_HIGH_PRIO;
			}
			else if(transVal_real>=5000){
				this.priority = HIGH_PRIO;
			}
			else if(transVal_real>=1000){
				this.priority = NORMAL_PRIO;
			}
			else{
				this.priority = LOW_PRIO;
			}
		} catch (SQLException se) {
			log.error("Unexpected SQLException in DELIVERY_BG");
			for (SQLException x = se; x != null; x = x.getNextException())
				log.error(x.getMessage());
			se.printStackTrace();

			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
		} catch (Exception e) {
			try {
				db.rollback();
				SQLString += "Abort;\",\n";
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
			throw e;
		}
	}

	private void traceDeliveryBG(Logger log, Formatter fmt[]) {
		fmt[0].format("                                    DeliveryBG");
		fmt[1].format("Warehouse: %6d", deliveryBG.w_id);
		fmt[2].format("Carrier Number: %2d", deliveryBG.o_carrier_id);
		fmt[3].format("Delivery Date: %-19.19s", deliveryBG.ol_delivery_d);

		if (transEnd != 0) {
			for (int d_id = 1; d_id <= 10; d_id++) {
				fmt[4 + d_id].format("District %02d: delivered O_ID: %8d",
						d_id, deliveryBG.delivered_o_id[d_id - 1]);
			}
		}
	}

	public int[] getDeliveredOrderIDs() {
		return deliveryBG.delivered_o_id;
	}

	public int getSkippedDeliveries() {
		int numSkipped = 0;

		for (int i = 0; i < 10; i++) {
			if (deliveryBG.delivered_o_id[i] < 0)
				numSkipped++;
		}

		return numSkipped;
	}

	private class DeliveryBGData {
		/* DELIVERY_BG data */
		public int w_id;
		public int o_carrier_id;
		public String ol_delivery_d;

		public int delivered_o_id[];
		public int delivered_c_id[];
		public double sum_ol_amount[];
	}

	public int get_abort(){
		return abort;
	}

	private void transactionFromJson(String JsonLine){
		this.transGeneratime = System.currentTimeMillis();
		int head = JsonLine.indexOf(":")+1;
		int tail = JsonLine.length()-1;
		JsonLine  = JsonLine.substring(head, tail);
		JSONObject SQLJson = new JSONObject(JsonLine);
		//System.out.println(SQLJson);
		String QueryString = SQLJson.getString("sql");
		String[] Querys = QueryString.split(";");
		for(int i = 1;i<Querys.length;i++){//去掉Begin和Commit
				Querylist.add(Querys[i]);
		}
		String ValueString = SQLJson.getString("value");
		this.transVal_real = Double.parseDouble(ValueString);

		String type = SQLJson.getString("transType");
		String arriveString = SQLJson.getString("generateTime");
		long generateTime = Long.parseLong(arriveString);
		String priorityString = SQLJson.getString("priority");
		priority = Integer.parseInt(priorityString);
		this.transGeneratime = this.sessionTimestamp + generateTime;
		if(type.equals("New-Order")){
			this.transType = TT_NEW_ORDER;
		}
		else if(type.equals("Payment")){
			this.transType = TT_PAYMENT;
		}
		else if(type.equals("Stock-Level")){
			this.transType = TT_STOCK_LEVEL;
		}
		else if(type.equals("Order-Status")){
			this.transType = TT_ORDER_STATUS;
		}
		else{
			this.transType = TT_DELIVERY;
		}
	}


	public void executeStandardQuery(Logger log, String JsonString,long sessionStart,jTPCCConnection db,jTPCCRandom rnd)throws Exception{
		this.sessionTimestamp = sessionStart;
		transactionFromJson(JsonString);
		long currentTimestamp = System.currentTimeMillis();
		while(currentTimestamp<transGeneratime){
			currentTimestamp = System.currentTimeMillis();
		}

		double w = 0;
		switch (this.transType) {
			case TT_NEW_ORDER:
				w = 0.008;
				break;
			case TT_PAYMENT:
				w = 0.017;
				break;
			case TT_STOCK_LEVEL:
				w = 0.007;
				break;
			case TT_ORDER_STATUS:
				w = 0.003;
				break;
			case TT_DELIVERY:
				w = 0.005;
				break;
			default:
				break;
		}
		if(timecounter == 1){
			timeCounterNow = System.currentTimeMillis();
			timeDelta = timeCounterNow - this.transGeneratime;
			if (timeDelta > 200) {
				abortPoss = (timeDelta - 200) / (0.1 * 200) * w;
				if (abortPoss > 1) {
					abortPoss = 1;
				}
				int poss = rnd.nextInt(1, 100);
				if (poss < 100 * abortPoss) {
					abort = 1;
					loss_v+=this.transVal_real;
					// db.rollback();
					// SQLString += "Abort;\",\n";
					return;
				}
			}
		}
		int len = Querylist.size();
		String Query = "";
		double typeWeight = 0;
		try {
			this.transStart = System.currentTimeMillis();
			this.transDue = this.transStart;
			for(int i  = 0;i<len -2 ;i++){
				Query = Querylist.get(i);
				db.setStandardQuery(Query);
				PreparedStatement stmt = db.stmtStandardQuery;
				stmt.execute();
				stmt.close();
				if(timecounter == 1){
					timeCounterNow = System.currentTimeMillis();
					timeDelta = timeCounterNow - this.transGeneratime;
					if (timeDelta > 200) {
						abortPoss = (timeDelta - 200) / (0.1 * 200) * w;
						if (abortPoss > 1) {
							abortPoss = 1;
						}
						int poss = rnd.nextInt(1, 100);
						if (poss < 100 * abortPoss) {
							abort = 1;
							loss_v+=this.transVal_real;
							db.rollback();
							SQLString += "Abort;\",\n";
							return;
						}
					}
				}
			}
			Query = Querylist.get(len-1);
			if(Query.equals("Abort")){
				this.abort = 1;
				db.rollback();
				this.transEnd = System.currentTimeMillis();
			}
			else{
				db.commit();
				this.transEnd = System.currentTimeMillis();
				if(value_loss){
					timeCounterNow = System.currentTimeMillis();
						timeDelta = timeCounterNow - this.transGeneratime;
						if(transType == TT_NEW_ORDER || transType == TT_PAYMENT){
							if(transType == TT_NEW_ORDER){
								typeWeight = 0.008;
							}
							if(transType == TT_PAYMENT){
								typeWeight = 0.017;
							}
							if (timeDelta > 200) {
								double valloss = transVal_real*(timeDelta - 200) / (0.1 * 200) * typeWeight;
								if(valloss<transVal_real){
									transVal_real = transVal_real-transVal_real*(timeDelta - 200) / (0.1 * 200) * typeWeight;
								}
								else{
									transVal_real = 0;
								}
							}
						}
				}
			}
		}catch (SQLException se) {
			log.error("Unexpected SQLException");
			log.error(Query);
			this.abort = 1;
			for (SQLException x = se; x != null; x = x.getNextException())
				log.error(x.getMessage());
			se.printStackTrace();

			try {
				db.rollback();
				this.transEnd = System.currentTimeMillis();
				this.abort = 1;
				this.transVal_real = 0;
			} catch (SQLException se2) {
				throw new Exception("Unexpected SQLException on rollback: " +
						se2.getMessage());
			}
			} catch (Exception e) {
				try {
					db.rollback();
					this.transEnd = System.currentTimeMillis();
					this.abort = 1;
					this.transVal_real = 0;
				} catch (SQLException se2) {
					throw new Exception("Unexpected SQLException on rollback: " +
							se2.getMessage());
				}
				throw new Exception(Query + "\nUnexpected Exception :" +
				e.getMessage());
			}
	}

	public String getTransType(){
		return transTypeNames[this.transType];
	}


	public void executeStandardQuery_Heap(Logger log, String SQLString,int transType,double transVal,long generateTime, long sessionTimestamp,int priority,jTPCCConnection db, jTPCCRandom rnd)throws Exception{
		this.transType = transType;
		this.transVal_real = transVal;
		this.sessionTimestamp = sessionTimestamp;
		this.transGeneratime = sessionTimestamp + generateTime;
		this.priority = priority;
		double typeWeight = 0;
		String[] Querys = SQLString.split(";");
		for(int i = 1;i<Querys.length;i++){//去掉Begin和Commit
				Querylist.add(Querys[i]);
		}
		int len = Querylist.size();
		String Query = "";

		double w = 0;
		switch (this.transType) {
			case TT_NEW_ORDER:
				w = 0.008;
				break;
			case TT_PAYMENT:
				w = 0.017;
				break;
			case TT_STOCK_LEVEL:
				w = 0.007;
				break;
			case TT_ORDER_STATUS:
				w = 0.003;
				break;
			case TT_DELIVERY:
				w = 0.005;
				break;
			default:
				break;
		}
		if(timecounter == 1){
			timeCounterNow = System.currentTimeMillis();
			timeDelta = timeCounterNow - this.transGeneratime;
			if (timeDelta > 200) {
				abortPoss = (timeDelta - 200) / (0.1 * 200) * w;
				if (abortPoss > 1) {
					abortPoss = 1;
				}
				int poss = rnd.nextInt(1, 100);
				if (poss < 100 * abortPoss) {
					abort = 1;
					loss_v+=this.loss_v;
					// db.rollback();
					// SQLString += "Abort;\",\n";

					return;
				}
			}
		}
		int retry = 0;
		boolean success = false;
		int maxRetry = 5;
		while(!success && retry < maxRetry){
			try {
				this.transStart = System.currentTimeMillis();
				this.transDue = this.transStart;
				for(int i  = 0;i<len -2 ;i++){
					Query = Querylist.get(i);
					db.setStandardQuery(Query);
					PreparedStatement stmt = db.stmtStandardQuery;
					stmt.execute();
					stmt.close();
					if(timecounter == 1){
						timeCounterNow = System.currentTimeMillis();
						timeDelta = timeCounterNow - this.transGeneratime;
						if (timeDelta > 200) {
							abortPoss = (timeDelta - 200) / (0.1 * 200) * w;
							if (abortPoss > 1) {
								abortPoss = 1;
							}
							int poss = rnd.nextInt(1, 100);
							if (poss < 100 * abortPoss) {
								abort = 1;
								loss_v+=this.transVal_real;
								db.rollback();
								SQLString += "Abort;\",\n";
								return;
							}
						}
					}
				}
				Query = Querylist.get(len-1);
				if(Query.equals("Abort")){
					this.abort = 1;
					transVal = 0;
					db.rollback();
					this.transEnd = System.currentTimeMillis();
				}
				else{
					db.commit();
					this.transEnd = System.currentTimeMillis();
					if(value_loss){
						timeCounterNow = System.currentTimeMillis();
							timeDelta = timeCounterNow - this.transGeneratime;
							if(transType == TT_NEW_ORDER || transType == TT_PAYMENT){
								if(transType == TT_NEW_ORDER){
									typeWeight = 0.008;
								}
								if(transType == TT_PAYMENT){
									typeWeight = 0.017;
								}
								if (timeDelta > 200) {
									double valloss = transVal_real*(timeDelta - 200) / (0.1 * 200) * typeWeight;
									if(valloss<transVal_real){
										transVal_real = transVal_real-transVal_real*(timeDelta - 200) / (0.1 * 200) * typeWeight;
									}
									else{
										transVal_real = 0;
									}
								}
							}
					}
				}
				success = true;
			}catch (SQLException se) {
				log.error("Unexpected SQLException");
				log.error(Query);
				for (SQLException x = se; x != null; x = x.getNextException())
					log.error(x.getMessage());
				se.printStackTrace();

				String sqlState = se.getSQLState();
				int errorCode = se.getErrorCode();
				if("40001".equals(sqlState) || "40P01".equals(sqlState) || errorCode == 1213){
					retry++;
					System.err.println("Deadlock detected (" + sqlState + "), retrying... attempt " + retry);
				}else{
					this.abort = 1;
					success = true;
				}	


				try {
					db.rollback();
					this.transEnd = System.currentTimeMillis();
					this.transVal_real = 0;
				} catch (SQLException se2) {
					throw new Exception("Unexpected SQLException on rollback: " +
							se2.getMessage());
				}
			} 
			catch (Exception e) {
				try {
					db.rollback();
					this.abort = 1;
					this.transEnd = System.currentTimeMillis();
					this.transVal_real = 0;
				} catch (SQLException se2) {
					throw new Exception("Unexpected SQLException on rollback: " +
							se2.getMessage());
				}
				throw new Exception(Query + "\nUnexpected Exception :" +
				e.getMessage());
			}
		}

	}
	public long get_transEndTime(){
		return this.transEnd;
	}

	public long get_transGenerateTime(){
		return this.transGeneratime;
	}

	public int get_priority(){
		return priority;
	}

	private String buildDebugSql(String sql,List<Object> params){
		if (params == null) return sql;
		for (Object param : params) {
			String value;
			if (param == null) {
				value = "NULL";
			} else if (param instanceof String || 
					   param instanceof java.sql.Date || 
					   param instanceof java.time.LocalDate || 
					   param instanceof java.sql.Timestamp) {
				value = "'" + param.toString().replace("'", "''") + "'";
			} else {
				value = param.toString();
			}
			sql = sql.replaceFirst("\\?", value);
		}
		return sql;
	}

	public void executeOraclePre(jTPCCConnection db){
		try{
			PreparedStatement stmt;
			stmt = db.stmtOracleSetNLSTimeStamp;
			stmt.executeQuery();
		}
		catch (Exception e) {
			try {
				db.rollback();
				this.abort = 1;
				this.transEnd = System.currentTimeMillis();
				this.transVal_real = 0;
			} catch (SQLException se) {
				se.printStackTrace();
			}
		}
	}

	public double get_loss_v(){
		return this.loss_v;
	}


	public void setNewOrderData(int w_id, int d_id, int c_id, int o_ol_cnt, int[] ol_supply_w_id, int[] ol_i_id, int[] ol_quantity){
		this.newOrder.w_id = w_id;
		this.newOrder.d_id = d_id;
		this.newOrder.c_id = c_id;
		this.newOrder.o_ol_cnt = o_ol_cnt;
		this.newOrder.ol_supply_w_id = ol_supply_w_id;
		this.newOrder.ol_i_id = ol_i_id;
		this.transType = TT_NEW_ORDER;
	}

	public void setOrderStatusData(int w_id, int d_id, int c_id, String c_last){
		this.orderStatus.w_id = w_id;
		this.orderStatus.d_id = d_id;
		this.orderStatus.c_id = c_id;
		this.orderStatus.c_last = c_last;
		this.transType = TT_ORDER_STATUS;
	}

	public void setStockLevelData(int w_id, int d_id, int threshold){
		this.stockLevel.w_id = w_id;
		this.stockLevel.d_id = d_id;
		this.stockLevel.threshold = threshold;
		this.transType = TT_STOCK_LEVEL;
	}

	public void setDeliveryData(int w_id, int o_carrier_id){
		this.delivery.w_id = w_id;
		this.delivery.o_carrier_id = o_carrier_id;
		this.transType = TT_DELIVERY;
	}

	public double get_transOrderVal(){
		return this.transOrderVal;
	}

	public int get_deliveryOrderCount(){
		return this.DeliveryOrderCount;
	}

}