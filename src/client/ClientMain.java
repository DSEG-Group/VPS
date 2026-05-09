package client;

import java.io.DataOutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class ClientMain {
    private static int port = 9876;
    private static ExecutorService threadPool;
    static boolean running  = true;
    private static long testtime = 60*1000;
    private static int task_interval_ms = 500/128;
    public final static int TT_NEW_ORDER = 0,
                            TT_PAYMENT = 1,
                            TT_ORDER_STATUS = 2,
                            TT_DELIVERY = 3,
                            TT_STOCK_LEVEL = 4;

    private static int new_order_w = 42;
    private static int payment_w   = 45;
    private static int order_status_w = 4;
    private static int stock_level_w  = 4;
    private static int delivery_w     = 4;


    public static void main(String[] args) {
        jTPCCRandom rnd = new jTPCCRandom();
        threadPool = Executors.newFixedThreadPool(128);
        long start_time = System.currentTimeMillis();
        long now_time = System.currentTimeMillis();
        int max_warehouse = 10;
        while(now_time-start_time<testtime){
            int pos = rnd.nextInt(1, 100);
            // int pos = 99;
            int transactionType;
            if(pos<new_order_w){
                transactionType = TT_NEW_ORDER;
            }
            else{
                if(pos<new_order_w+payment_w){
                    transactionType = TT_PAYMENT;
                }
                else{
                    if(pos<new_order_w+payment_w+order_status_w){
                        transactionType = TT_ORDER_STATUS;
                    }
                    else{
                        if(pos<new_order_w+payment_w+order_status_w+stock_level_w){
                            transactionType = TT_STOCK_LEVEL;
                        }
                        else{
                            transactionType = TT_DELIVERY;
                        }
                    }
                }
            }
            long before = System.currentTimeMillis();
            threadPool.submit(new ClientRunner("127.0.0.1", port,transactionType,max_warehouse,rnd));
            now_time = System.currentTimeMillis();
            long sleep = task_interval_ms - (now_time - before); 
            if(sleep>0){
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            now_time = System.currentTimeMillis();
        }
        try{
            Socket server = new Socket("127.0.0.1", port);
            DataOutputStream dos = new DataOutputStream(server.getOutputStream());
            dos.writeInt(-1);
            dos.flush();
            server.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
        threadPool.shutdown();
    }

    public static void closeClient() {
        running = false;
    }
}
