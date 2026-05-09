package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import javax.print.attribute.standard.Severity;

import org.apache.log4j.*;

import client.jTPCC;
import client.jTPCCConfig;
import client.jTPCCConnection;
import client.jTPCCRandom;
import client.jTPCCTData;
import client.jTPCCTerminal;

import java.io.IOException;
import java.io.DataInputStream;

public class DBconnetRunner implements Runnable {
    public final static int TT_NEW_ORDER = 0,
                        TT_PAYMENT = 1,
                        TT_ORDER_STATUS = 2,
                        TT_DELIVERY = 3,
                        TT_STOCK_LEVEL = 4;
    private ServerRuner.NewOrderData newOrderData;
    private ServerRuner.PaymentData paymentData;
    private ServerRuner.OrderStatusData orderStatusData;
    private ServerRuner.DeliveryData deliveryData;
    private ServerRuner.StockLevelData stockLevelData;
    private Connection  conn;
    private jTPCCConnection db;
    private jTPCCRandom rnd;
    private static org.apache.log4j.Logger log = Logger.getLogger(jTPCCTerminal.class);
    private int id;
    private boolean running = true;
    private double transValue;
    private int DeliveryOrderCount;

    public DBconnetRunner(int id, Connection conn,jTPCCRandom rnd){
        this.id = id;
        this.conn = conn;
        this.rnd = rnd;
        
    }

    public void stop(){
        running = false;
    }

    @Override
    public void run(){
        try{
            this.db = new jTPCCConnection(this.conn, jTPCCConfig.DB_POSTGRES);
            while(running){
                if(VPSServer.requestQueue.isEmpty()){
                    continue;
                }
                else{
                    //可以再次添加调度方案。
                    QueueNode node = VPSServer.requestQueue.dequeue();
                    int transType = node.getTransactionType();
                    jTPCCTData term = new jTPCCTData();
                    switch(transType){
                        case TT_NEW_ORDER:
                            DBexecuteNewOrder(node,term);
                            break;
                        case TT_PAYMENT:
                            DBexecutePayment(node,term);
                            break;
                        case TT_STOCK_LEVEL:
                            DBexecuteStock(node,term);
                            break;
                        case TT_DELIVERY:
                            DBexecuteDelivery(node,term);
                            break;
                        case TT_ORDER_STATUS:
                            DBexecuteOrderStatus(node,term);
                            break;
                        default:
                            break;

                    }
                    int isAbort = term.get_abort();
                    double transVal = term.getTransVal_real();
                    VPSServer.TransactionReport(isAbort, transVal);
                    VPSServer.updateContext(transType, transVal, this.DeliveryOrderCount,isAbort);
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
                
    }

    private void DBexecuteNewOrder(QueueNode node, jTPCCTData term)throws Exception{
        newOrderData = node.getNewOrderData();
        term.setNewOrderData(newOrderData.w_id, newOrderData.d_id, newOrderData.c_id, newOrderData.o_ol_cnt, newOrderData.ol_supply_w_id, newOrderData.ol_i_id, newOrderData.ol_quantity);
        term.executeNewOrder(log, db, this.rnd);
    }

    private void DBexecutePayment(QueueNode node, jTPCCTData term)throws Exception{
        term.executePayment(log,db,this.rnd);
    }

    private void DBexecuteOrderStatus(QueueNode node, jTPCCTData term)throws Exception{
        orderStatusData = node.getOrderStatusData();
        term.setOrderStatusData(orderStatusData.w_id, orderStatusData.d_id, orderStatusData.c_id,orderStatusData.c_last);
        term.executeOrderStatus(log, db);
    }

    private void DBexecuteStock(QueueNode node, jTPCCTData term)throws Exception{
        stockLevelData = node.getStockLevelData();
        term.setStockLevelData(stockLevelData.w_id, stockLevelData.d_id, stockLevelData.threshold);
        term.executeStockLevel(log, db);
    }

    private void DBexecuteDelivery(QueueNode node, jTPCCTData term)throws Exception{
        deliveryData = node.getDeliveryData();
        term.setDeliveryData(deliveryData.w_id, deliveryData.o_carrier_id);
        term.executeDelivery(log, db);
        

        jTPCCTData  bg = term.getDeliveryBG();
		bg.traceScreen(log);
		bg.executeDeliveryBG(log, db);
		bg.traceScreen(log);
        this.DeliveryOrderCount = term.get_deliveryOrderCount();
    }


}