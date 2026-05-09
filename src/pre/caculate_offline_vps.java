package pre;

import OSCollector.OSCollector;
import client.jTPCC;

import org.apache.log4j.*;
import org.firebirdsql.jdbc.parser.JaybirdSqlParser.substringFunction_return;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.text.*;
import java.util.stream.Stream;
import client.Heap;
import client.TreeNode;

public class caculate_offline_vps {
    private static org.apache.log4j.Logger log = Logger.getLogger(jTPCC.class);
    private int threadsnum = 16;
    private BufferedReader SQLFileReader = null;
    public Heap tree;
    private long sessionStart;
    private Object counterLock = new Object();
    private int transactionCount = 0;
    private double transValCount = 0;
    private String file_path = "/home/lyb/vps_benchmark/run/standard_data/mysql_result.json";

    public final static int TT_NEW_ORDER = 0,
							TT_PAYMENT = 1,
							TT_STOCK_LEVEL = 4,
							TT_DELIVERY = 3,
							TT_ORDER_STATUS = 2;

    public static void main(String args []){
        new caculate_offline_vps();
    }

    public caculate_offline_vps(){
        this.tree = new Heap();
        this.sessionStart = System.currentTimeMillis();
        try {
            SQLFileReader = new BufferedReader(new FileReader(file_path));
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        workerthread thread_read = new workerthread(true, file_path, this);
        workerthread thread_caculate = new workerthread(false, file_path, this);
        synchronized (thread_read) {
            (new Thread(thread_read)).start();
            // (new Thread(thread_caculate)).start();
        }
    }

    public long getSessionStart(){
        return this.sessionStart;
    }
    
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

    private void errorMessage(String message) {
		log.error("Term-00, " + message);
	}

    // public void signalTerminalEndedTransaction(double transVal,int is_abort){
    //     synchronized (counterLock) {
    //             transactionCount++;
    //             transValCount += transVal;// change 11.13
    //     }
    //     if(total_txn !=0 && total_txn<=transactionCount){
    //         signalTerminalsRequestEnd(true);
    //     }
    //     if(limitValue != 0 && limitValue <= transValCount){
    //         signalTerminalsRequestEnd(true);
    //     }

    //     if (sessionEndTargetTime != -1 && System.currentTimeMillis() > sessionEndTargetTime) {
    //         signalTerminalsRequestEnd(true);
    //     }

    //     updateStatusLine();
    // }
}
