package client;/*
 * jTPCCConnection
 *
 * One connection to the database. Used by either the old style
 * Terminal or the new TimedSUT.
 *
 * Copyright (C) 2004-2016, Denis Lussier
 * Copyright (C) 2016, Jan Wieck
 *
 */

import java.util.*;
import java.sql.*;

public class jTPCCConnection
{
    private Connection          dbConn = null;
    private int                 dbType = 0;

    public PreparedStatement    stmtNewOrderSelectWhseCust;
    public PreparedStatement    stmtNewOrderSelectDist;
    public PreparedStatement    stmtNewOrderUpdateDist;
    public PreparedStatement    stmtNewOrderInsertOrder;
    public PreparedStatement    stmtNewOrderInsertNewOrder;
    public PreparedStatement    stmtNewOrderSelectStock;
    public PreparedStatement    stmtNewOrderSelectStockBatch[];
    public PreparedStatement    stmtNewOrderSelectItem;
    public PreparedStatement    stmtNewOrderSelectItemBatch[];
    public PreparedStatement    stmtNewOrderUpdateStock;
    public PreparedStatement    stmtNewOrderInsertOrderLine;
	public PreparedStatement    stmtNewOrderUpdateCustomer;

    public PreparedStatement    stmtPaymentSelectWarehouse;
    public PreparedStatement    stmtPaymentSelectDistrict;
    public PreparedStatement    stmtPaymentSelectCustomerListByLast;
    public PreparedStatement    stmtPaymentSelectCustomer;
    public PreparedStatement    stmtPaymentSelectCustomerData;
    public PreparedStatement    stmtPaymentUpdateWarehouse;
    public PreparedStatement    stmtPaymentUpdateDistrict;
    public PreparedStatement    stmtPaymentUpdateCustomer;
    public PreparedStatement    stmtPaymentUpdateCustomerWithData;
    public PreparedStatement    stmtPaymentInsertHistory;
	public PreparedStatement    stmtPaymentSelectNewOrder;
	public PreparedStatement    stmtPaymentUpdateNewOrder;
	public PreparedStatement	stmtPaymentSelectOorderData;
	public PreparedStatement    stmtPaymentSelectOrderLineAmount;

    public PreparedStatement    stmtOrderStatusSelectCustomerListByLast;
    public PreparedStatement    stmtOrderStatusSelectCustomer;
    public PreparedStatement    stmtOrderStatusSelectLastOrder;
    public PreparedStatement    stmtOrderStatusSelectOrderLine;

    public PreparedStatement    stmtStockLevelSelectLow;

	public PreparedStatement    stmtDeliveryBGSelectOldestNewOrder;
    public PreparedStatement    stmtDeliveryBGDeleteOldestNewOrder;
    public PreparedStatement    stmtDeliveryBGSelectOrder;
    public PreparedStatement    stmtDeliveryBGUpdateOrder;
    public PreparedStatement    stmtDeliveryBGSelectSumOLAmount;
    public PreparedStatement    stmtDeliveryBGUpdateOrderLine;
    public PreparedStatement    stmtDeliveryBGUpdateCustomer;

	public PreparedStatement	stmtSetPriorityHigh;
	public PreparedStatement	stmtSetPriorityLow;
	public PreparedStatement	stmtSetPriorityNormal;

    public jTPCCConnection(Connection dbConn, int dbType)
	throws SQLException
    {
	this.dbConn = dbConn;
	this.dbType = dbType;
	
	stmtSetPriorityHigh = dbConn.prepareStatement("SET TRANSACTION PRIORITY HIGH");
	stmtSetPriorityLow = dbConn.prepareStatement("SET TRANSACTION PRIORITY LOW");
	stmtSetPriorityNormal = dbConn.prepareStatement("SET TRANSACTION PRIORITY NORMAL");


	stmtNewOrderSelectStockBatch = new PreparedStatement[16];
	String st = "SELECT s_i_id, s_w_id, s_quantity, s_data, \n" +
				"       s_dist_01, s_dist_02, s_dist_03, s_dist_04, \n" +
				"       s_dist_05, s_dist_06, s_dist_07, s_dist_08, \n" +
				"       s_dist_09, s_dist_10 \n" +
				"    FROM bmsql_stock \n" +
				"    WHERE (s_w_id, s_i_id) in ((?,?)";
	for (int i = 1; i <= 15; i ++) {
		String stmtStr = st + ") FOR UPDATE";
		stmtNewOrderSelectStockBatch[i] = dbConn.prepareStatement(stmtStr);
		st += ",(?,?)";
	}
	stmtNewOrderSelectItemBatch = new PreparedStatement[16];
	st = "SELECT i_id, i_price, i_name, i_data \n" +
			"    FROM bmsql_item WHERE i_id in (?";
	for (int i = 1; i <= 15; i ++) {
		String stmtStr = st + ")";
		stmtNewOrderSelectItemBatch[i] = dbConn.prepareStatement(stmtStr);
		st += ",?";
	}

	// PreparedStataments for NEW_ORDER
	stmtNewOrderSelectWhseCust = dbConn.prepareStatement(
		"SELECT c_discount, c_last, c_credit, w_tax \n" +
		"    FROM bmsql_customer \n" +
		"    JOIN bmsql_warehouse ON (w_id = c_w_id) \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
	stmtNewOrderSelectDist = dbConn.prepareStatement(
		"SELECT d_tax, d_next_o_id \n" +
		"    FROM bmsql_district \n" +
		"    WHERE d_w_id = ? AND d_id = ? \n" +
		"    FOR UPDATE");
	stmtNewOrderUpdateDist = dbConn.prepareStatement(
		"UPDATE bmsql_district \n" +
		"    SET d_next_o_id = d_next_o_id + 1 \n" +
		"    WHERE d_w_id = ? AND d_id = ?");
	stmtNewOrderInsertOrder = dbConn.prepareStatement(
		"INSERT INTO bmsql_oorder (\n" +
		"    o_id, o_d_id, o_w_id, o_c_id, o_entry_d, \n" +
		"    o_ol_cnt, o_all_local) \n" +
		"    VALUES (?, ?, ?, ?, ?, ?, ?)");
	stmtNewOrderInsertNewOrder = dbConn.prepareStatement(
		"INSERT INTO bmsql_new_order (\n" +
		"    no_o_id, no_d_id, no_w_id, no_p_flag) \n" +
		"    VALUES (?, ?, ?, 0)");
	stmtNewOrderSelectStock = dbConn.prepareStatement(
		"SELECT s_quantity, s_data, \n" +
		"       s_dist_01, s_dist_02, s_dist_03, s_dist_04, \n" +
		"       s_dist_05, s_dist_06, s_dist_07, s_dist_08, \n" +
		"       s_dist_09, s_dist_10 \n" +
		"    FROM bmsql_stock \n" +
		"    WHERE s_w_id = ? AND s_i_id = ? \n" +
		"    FOR UPDATE");
	stmtNewOrderSelectItem = dbConn.prepareStatement(
		"SELECT i_price, i_name, i_data \n" +
		"    FROM bmsql_item \n" +
		"    WHERE i_id = ?");
	stmtNewOrderUpdateStock = dbConn.prepareStatement(
		"UPDATE bmsql_stock \n" +
		"    SET s_quantity = ?, s_ytd = s_ytd + ?, \n" +
		"        s_order_cnt = s_order_cnt + 1, \n" +
		"        s_remote_cnt = s_remote_cnt + ? \n" +
		"    WHERE s_w_id = ? AND s_i_id = ?");
	stmtNewOrderInsertOrderLine = dbConn.prepareStatement(
		"INSERT INTO bmsql_order_line (\n" +
		"    ol_o_id, ol_d_id, ol_w_id, ol_number, \n" +
		"    ol_i_id, ol_supply_w_id, ol_quantity, \n" +
		"    ol_amount, ol_dist_info) \n" +
		"    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
	stmtNewOrderUpdateCustomer = dbConn.prepareStatement(
		"UPDATE bmsql_customer \n" +
		"    SET c_balance = c_balance + ? \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");

	// PreparedStatements for PAYMENT
	stmtPaymentSelectNewOrder = dbConn.prepareStatement(
		"SELECT no_o_id, no_w_id, no_d_id\n"+
		"   FROM bmsql_new_order \n"+
		"   WHERE no_p_flag = 0 \n"+
		" 	ORDER BY RAND()\n" +
		"	LIMIT 1");	
	stmtPaymentUpdateNewOrder = dbConn.prepareStatement(
		"UPDATE bmsql_new_order\n"+
		"    SET no_p_flag = 1\n"+
		"    WHERE no_o_id = ? AND no_w_id = ? AND no_d_id = ?");
	stmtPaymentSelectOorderData = dbConn.prepareStatement(
		"SELECT o_c_id\n"+
		"	FROM bmsql_oorder\n"+
		"	WHERE o_id = ? AND o_w_id = ? AND o_d_id = ?");
	stmtPaymentSelectOrderLineAmount = dbConn.prepareStatement(
		"SELECT ol_amount\n"+
		"	FROM bmsql_order_line\n"+
		"	WHERE ol_o_id = ? AND ol_w_id = ? AND ol_d_id = ?");
	stmtPaymentSelectWarehouse = dbConn.prepareStatement(
		"SELECT w_name, w_street_1, w_street_2, w_city, \n" +
		"       w_state, w_zip \n" +
		"    FROM bmsql_warehouse \n" +
		"    WHERE w_id = ? ");
	stmtPaymentSelectDistrict = dbConn.prepareStatement(
		"SELECT d_name, d_street_1, d_street_2, d_city, \n" +
		"       d_state, d_zip \n" +
		"    FROM bmsql_district \n" +
		"    WHERE d_w_id = ? AND d_id = ?");
	stmtPaymentSelectCustomerListByLast = dbConn.prepareStatement(
		"SELECT c_id \n" +
		"    FROM bmsql_customer \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_last = ? \n" +
		"    ORDER BY c_first");
	stmtPaymentSelectCustomer = dbConn.prepareStatement(
		"SELECT c_first, c_middle, c_last, c_street_1, c_street_2, \n" +
		"       c_city, c_state, c_zip, c_phone, c_since, c_credit, \n" +
		"       c_credit_lim, c_discount, c_balance \n" +
		"    FROM bmsql_customer \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ? \n" +
		"    FOR UPDATE");
	stmtPaymentSelectCustomerData = dbConn.prepareStatement(
		"SELECT c_data \n" +
		"    FROM bmsql_customer \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
	stmtPaymentUpdateWarehouse = dbConn.prepareStatement(
		"UPDATE bmsql_warehouse \n" +
		"    SET w_ytd = w_ytd + ? \n" +
		"    WHERE w_id = ?");
	stmtPaymentUpdateDistrict = dbConn.prepareStatement(
		"UPDATE bmsql_district \n" +
		"    SET d_ytd = d_ytd + ? \n" +
		"    WHERE d_w_id = ? AND d_id = ?");
	stmtPaymentUpdateCustomer = dbConn.prepareStatement(
		"UPDATE bmsql_customer \n" +
		"    SET c_balance = c_balance - ?, \n" +
		"        c_ytd_payment = c_ytd_payment + ?, \n" +
		"        c_payment_cnt = c_payment_cnt + 1 \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
	stmtPaymentUpdateCustomerWithData = dbConn.prepareStatement(
		"UPDATE bmsql_customer \n" +
		"    SET c_balance = c_balance - ?, \n" +
		"        c_ytd_payment = c_ytd_payment + ?, \n" +
		"        c_payment_cnt = c_payment_cnt + 1, \n" +
		"        c_data = ? \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
	stmtPaymentInsertHistory = dbConn.prepareStatement(
		"INSERT INTO bmsql_history (\n" +
		"    h_c_id, h_c_d_id, h_c_w_id, h_d_id, h_w_id, \n" +
		"    h_date, h_amount, h_data) \n" +
		"    VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

	// PreparedStatements for ORDER_STATUS
	stmtOrderStatusSelectCustomerListByLast = dbConn.prepareStatement(
		"SELECT c_id \n" +
		"    FROM bmsql_customer \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_last = ? \n" +
		"    ORDER BY c_first");
	stmtOrderStatusSelectCustomer = dbConn.prepareStatement(
		"SELECT c_first, c_middle, c_last, c_balance \n" +
		"    FROM bmsql_customer \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
	stmtOrderStatusSelectLastOrder = dbConn.prepareStatement(
		"SELECT o_id, o_entry_d, o_carrier_id \n" +
		"    FROM bmsql_oorder \n" +
		"    WHERE o_w_id = ? AND o_d_id = ? AND o_c_id = ? \n" +
		"      ORDER BY o_id DESC LIMIT 1");
	stmtOrderStatusSelectOrderLine = dbConn.prepareStatement(
		"SELECT ol_i_id, ol_supply_w_id, ol_quantity, \n" +
		"       ol_amount, ol_delivery_d \n" +
		"    FROM bmsql_order_line \n" +
		"    WHERE ol_w_id = ? AND ol_d_id = ? AND ol_o_id = ? \n" +
		"    ORDER BY ol_w_id, ol_d_id, ol_o_id, ol_number");

	// PreparedStatements for STOCK_LEVEL
	switch (dbType)
	{
		case jTPCCConfig.DB_COCKROACH:
	    case jTPCCConfig.DB_POSTGRES:
	    case jTPCCConfig.DB_MYSQL:
		stmtStockLevelSelectLow = dbConn.prepareStatement(
		    "SELECT count(*) AS low_stock FROM (\n" +
		    "    SELECT s_w_id, s_i_id, s_quantity \n" +
		    "        FROM bmsql_stock \n" +
		    "        WHERE s_w_id = ? AND s_quantity < ? AND s_i_id IN (\n" +
		    "            SELECT /*+ TIDB_INLJ(bmsql_order_line) */ ol_i_id \n" +
		    "                FROM bmsql_district \n" +
		    "                JOIN bmsql_order_line ON ol_w_id = d_w_id \n" +
		    "                 AND ol_d_id = d_id \n" +
		    "                 AND ol_o_id >= d_next_o_id - 20 \n" +
		    "                 AND ol_o_id < d_next_o_id \n" +
		    "                WHERE d_w_id = ? AND d_id = ? \n" +
		    "        ) \n" +
		    "    ) AS L");
		break;

	    default:
		stmtStockLevelSelectLow = dbConn.prepareStatement(
		    "SELECT count(*) AS low_stock FROM (\n" +
		    "    SELECT s_w_id, s_i_id, s_quantity \n" +
		    "        FROM bmsql_stock \n" +
		    "        WHERE s_w_id = ? AND s_quantity < ? AND s_i_id IN (\n" +
		    "            SELECT ol_i_id \n" +
		    "                FROM bmsql_district \n" +
		    "                JOIN bmsql_order_line ON ol_w_id = d_w_id \n" +
		    "                 AND ol_d_id = d_id \n" +
		    "                 AND ol_o_id >= d_next_o_id - 20 \n" +
		    "                 AND ol_o_id < d_next_o_id \n" +
		    "                WHERE d_w_id = ? AND d_id = ? \n" +
		    "        ) \n" +
		    "    )");
		break;
	}


		// PreparedStatements for DELIVERY_BG
    stmtDeliveryBGSelectOldestNewOrder = dbConn.prepareStatement(
        "SELECT no_o_id \n" +
        "    FROM bmsql_new_order \n" +
        "    WHERE no_w_id = ? AND no_d_id = ? AND no_p_flag = 1\n" +
        "    ORDER BY no_o_id ASC\n" +
        "    LIMIT 1\n" +
        "    FOR UPDATE");
	stmtDeliveryBGDeleteOldestNewOrder = dbConn.prepareStatement(
		"DELETE FROM bmsql_new_order \n" +
		"    WHERE (no_w_id,no_d_id,no_o_id) IN (\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?),\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?))");

	stmtDeliveryBGSelectOrder = dbConn.prepareStatement(
		"SELECT o_c_id, o_d_id\n" +
		"    FROM bmsql_oorder \n" +
		"    WHERE (o_w_id,o_d_id,o_id) IN (\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?),\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?))");

	stmtDeliveryBGUpdateOrder = dbConn.prepareStatement(
		"UPDATE bmsql_oorder \n" +
		"    SET o_carrier_id = ? \n" +
		"    WHERE (o_w_id,o_d_id,o_id) IN (\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?),\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?))");

	stmtDeliveryBGSelectSumOLAmount = dbConn.prepareStatement(
		"SELECT sum(ol_amount) AS sum_ol_amount, ol_d_id\n" +
		"    FROM bmsql_order_line \n" +
		"    WHERE (ol_w_id,ol_d_id,ol_o_id) IN (\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?),\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?)\n" +
		") GROUP BY ol_d_id");


	stmtDeliveryBGUpdateOrderLine = dbConn.prepareStatement(
		"UPDATE bmsql_order_line \n" +
		"    SET ol_delivery_d = ? \n" +
		"    WHERE (ol_w_id,ol_d_id,ol_o_id) IN (\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?),\n" +
		"(?,?,?),(?,?,?),(?,?,?),(?,?,?),(?,?,?))");

	stmtDeliveryBGUpdateCustomer = dbConn.prepareStatement(
		"UPDATE bmsql_customer \n" +
		"    SET c_delivery_cnt = c_delivery_cnt + 1 \n" +
		"    WHERE c_w_id = ? AND c_d_id = ? AND c_id = ?");
    }

    public jTPCCConnection(String connURL, Properties connProps, int dbType)
	throws SQLException
    {
	this(DriverManager.getConnection(connURL, connProps), dbType);
    }

    public void commit()
	throws SQLException
    {
    	try {
			dbConn.commit();
		} catch(SQLException e) {
    		throw new CommitException();
		}
    }

    public void rollback()
	throws SQLException
    {
	dbConn.rollback();
    }

	public int getdbtype(){
		return this.dbType;
	}
}
