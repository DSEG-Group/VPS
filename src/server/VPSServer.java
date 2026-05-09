package server;

import java.util.*;

import org.apache.log4j.PropertyConfigurator;
import org.firebirdsql.jdbc.parser.JaybirdSqlParser.updateOrInsertStatement_return;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.*;
import client.jTPCCRandom;
import client.HotItemSketch;
import client.LRUCache;


public class VPSServer{
    private static ServerSocket serverSocket;
    private static ThreadPoolExecutor  threadPool;
    private static ThreadPoolExecutor  connectPool; 
    static boolean running  = true;
    static private String iConn = "jdbc:postgresql://0.0.0.0:11452/bmsql";
    private static String idb = "postgres";
    private static Properties dbProps = new Properties();
    private static Connection conn;
    private static jTPCCRandom rnd;
    private static double transVal_t = 0;
    private static int transCount = 0;
    private static Object counterLock = new Object();
    private static Object predLock = new Object();
    public static my_Queue requestQueue = new my_Queue(1024);
    private static HotItemSketch hot_item = new HotItemSketch(4, 65536);
    private static double limitTime = 60000;
    private static List<DBconnetRunner> runners;
    private static int notPayCounter;
    private static int PayCounter = 480000;
    private static double PayAvgValue = 19535.7136540625;
    private static double NewOrderAvgValue;
    private static Object updatelock = new Object();
    private static Set<Double> errorOrder = new HashSet<>();
    private static long predict_time = 0;
    private static long transaciton_in = 0;

    public final static int TT_NEW_ORDER = 0,
                        TT_PAYMENT = 1,
                        TT_ORDER_STATUS = 2,
                        TT_DELIVERY = 3,
                        TT_STOCK_LEVEL = 4;
    

    public static void main(String args[])throws InterruptedException, SQLException, IOException{
        // PropertyConfigurator.configure("log4j.properties");
        // CommandLineParser parser = new DefaultParser();
		// Options options =
        //     new Options()
        //             .addOption("c", "config", true, "[required] Workload configuration file")
        //             .addOption("d", "directory", true, "Base directory for the meta files")
        //             .addOption("s", "schema", true, "Base directory for the schema sql files")
        //             .addOption("p", "phase", true, "Online predict or offline training");
        // CommandLine argsLine = parser.parse(options, args);
        double startTime;
        double nowTime;
        dbProps.setProperty("user", "postgres");
		dbProps.setProperty("password", "123456");
        threadPool = new ThreadPoolExecutor(
                            128,
                            128,
                            60, TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(1000),
                            new ThreadPoolExecutor.AbortPolicy()
                        );
        connectPool = new ThreadPoolExecutor(
                            64,
                            128,
                            60, TimeUnit.SECONDS,
                            new ArrayBlockingQueue<>(1000),
                            new ThreadPoolExecutor.AbortPolicy()
                        );
        serverSocket = new ServerSocket(9876);
        conn = DriverManager.getConnection(iConn, dbProps);
        conn.setAutoCommit(false);
        rnd = new jTPCCRandom();
        runners = new ArrayList<>();
        for(int i = 0; i < 48; i++){
            DBconnetRunner runner = new DBconnetRunner(i, conn, rnd);
            runners.add(runner);
            connectPool.submit(runner);
        }
        startTime = System.currentTimeMillis();
        nowTime = System.currentTimeMillis();
        while(nowTime-startTime<limitTime){
            try{
                Socket clienSocket = serverSocket.accept();
                try {
                    threadPool.execute(new ServerRuner(clienSocket, 0, conn, rnd));
                } catch (RejectedExecutionException e) {
                    // 线程池满了，拒绝连接
                    clienSocket.close();
                }
            }
            catch(IOException e){
                e.printStackTrace();
            }
            finally{
                nowTime = System.currentTimeMillis();
            }
        }

        for(DBconnetRunner runner : runners){
            runner.stop();
        }
        
        threadPool.shutdown();
        if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
            threadPool.shutdownNow();
        }
        connectPool.shutdown();
        if (!connectPool.awaitTermination(60, TimeUnit.SECONDS)) {
            connectPool.shutdownNow();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
          serverSocket.close();
        }

        System.out.println("Total Transactions: " + String.valueOf(transCount));
        System.out.println("Total Transaction Value: " + String.valueOf(transVal_t));
        System.out.println("TPS: "+String.valueOf(transCount/limitTime/1000));
        System.out.println("VPS: "+String.valueOf(transVal_t/limitTime/1000));
        System.out.println("AvgPreTime: "+String.valueOf(predict_time));
        Path outputFile = Path.of("result.txt");

        List<String> lines = List.of(
                "Total Transactions: " + transCount,
                "Total Transaction Value: " + transVal_t,
                "TPS: "+String.valueOf(transCount/limitTime/1000),
                "VPS: "+String.valueOf(transVal_t/limitTime/1000),
                "AvgPreTime: "+String.valueOf(predict_time)
        );

        Files.write(outputFile, lines, StandardCharsets.UTF_8);
    }

    public static void closeServer() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
          serverSocket.close();
        }
    }

    public static void TransactionReport(int abort, double transaVal){
        synchronized (counterLock){
            if(abort == 0){
                transVal_t+=transaVal;
                transCount++;
            }
        }
    }

    public static boolean isHotItem(int i_id){
		return hot_item.isHotItem(i_id,3.0);
	}

	synchronized public static void updateHotItem(int i_id,double value)throws Exception{
        hot_item.update(i_id, value);
	}

    synchronized public static void updateContext(int transactionType,double transvalue,int DeliveryOrderCount,int isAbort){
        synchronized(updatelock){
            if(isAbort!=1){
                switch(transactionType){
                    case TT_NEW_ORDER:
                        notPayCounter++;
                        if(!errorOrder.contains(transvalue)){
                            if(transvalue == 0){
                                break;
                            }
                            NewOrderAvgValue = (NewOrderAvgValue*(notPayCounter-1)+transvalue)/notPayCounter;
                        }
                        else{
                            notPayCounter--;
                            errorOrder.remove(transvalue);
                        }
                        break;
                    case TT_PAYMENT:
                        double old_value = NewOrderAvgValue;
                        if(notPayCounter == 1){
                            if(old_value == transvalue){
                                NewOrderAvgValue = 0;
                                notPayCounter = 0;
                            }
                            else{
                                NewOrderAvgValue = old_value;
                                errorOrder.add(transvalue);
                            }
                        }
                        else{
                            NewOrderAvgValue = old_value*notPayCounter/(notPayCounter-1) - transvalue/(notPayCounter-1);
                            if(NewOrderAvgValue<=0){
                                NewOrderAvgValue = old_value;
                                errorOrder.add(transvalue);
                            }
                            else{
                                notPayCounter--;
                            }
                        }

                        PayCounter ++;
                        PayAvgValue = (PayAvgValue*(PayCounter-1)+transvalue)/PayCounter;
                        break;
                    case TT_DELIVERY:
                        if(PayCounter-DeliveryOrderCount > 0){
                            PayAvgValue = PayAvgValue/(PayCounter-DeliveryOrderCount)*PayCounter - transvalue*DeliveryOrderCount/(PayCounter-DeliveryOrderCount);
                            PayCounter-=DeliveryOrderCount;
                        }
                        else{
                            PayAvgValue=0;
                            PayCounter=0;
                        }
                        break;
                    default:
                        break;
                }
                if(Double.isNaN(PayAvgValue)||Double.isNaN(NewOrderAvgValue)||notPayCounter < 0){
                    System.err.println("error");
                }
            }
        }
    }

    public static double[] getContext(){
        synchronized(updatelock){
            double  context[] = {notPayCounter,NewOrderAvgValue,PayCounter,PayAvgValue};
            return context;
        }
    }

    public static void updatePredictTime(long time){
        synchronized(predLock){
            transaciton_in ++;
            predict_time += (time - predict_time) / transaciton_in;
        }
    }

} 