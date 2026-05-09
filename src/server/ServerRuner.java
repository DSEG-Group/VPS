package server;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.Thread;

import org.apache.log4j.*;

import client.jTPCC;
import client.jTPCCConfig;
import client.jTPCCConnection;
import client.jTPCCRandom;
import client.jTPCCTData;
import client.jTPCCTerminal;
import oracle.net.aso.l;

import java.io.IOException;
import java.io.DataInputStream;
import server.startModel;


public class ServerRuner implements Runnable{
    private final Socket clientSocket;
    private final int id;
    private int prio = 0;
    public final static int TT_NEW_ORDER = 0,
                            TT_PAYMENT = 1,
                            TT_ORDER_STATUS = 2,
                            TT_DELIVERY = 3,
                            TT_STOCK_LEVEL = 4;
    private final static String [] C_LAST_TOKEN = {
    "BAR", "OUGHT", "ABLE", "PRI", "PRES","ESE", "ANTI", "CALLY", "ATION", "EING"};

    private static final Map<String, Integer> TOKEN_INDEX = new HashMap<>();

                            
    private Connection  conn;
    private jTPCCConnection db;
    private jTPCCRandom rnd;
    private static org.apache.log4j.Logger log = Logger.getLogger(jTPCCTerminal.class);
    private double transactionValue = 0.0;
    private Map<String,Object>modelInput = new HashMap<>();
    private startModel myModel = new startModel();

    public ServerRuner(Socket clienSocket, int id, Connection conn,jTPCCRandom rnd){
        this.clientSocket = clienSocket;
        this.id = id;
        this.conn = conn;
        this.rnd = rnd;
        for (int i = 0; i < C_LAST_TOKEN.length; i++) {
            TOKEN_INDEX.put(C_LAST_TOKEN[i], i);
        }
    }

    @Override
    public void run() {
        // TODO Auto-generated method stub
        try {
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            int transactionType = in.readInt();
            
            this.db = new jTPCCConnection(this.conn, jTPCCConfig.DB_POSTGRES);
            // jTPCCTData term = new jTPCCTData();
            switch(transactionType){
                case TT_NEW_ORDER:
                    executeNewOrder(in);
                    break;
                case TT_PAYMENT:
                    executePayment(in);
                    break;
                case TT_ORDER_STATUS:
                    executeOrderStatus(in);
                    break;
                case TT_STOCK_LEVEL:
                    executeStockLevel(in);
                    break;
                case TT_DELIVERY:
                    executeDelivery(in);
                    break;
                default:
                    VPSServer.closeServer();
                    break;
            }
            
            // if(term.get_abort() == 0){
            //     transactionValue = term.getTransVal_real();
            // }else{
            //     transactionValue = 0.0;
            // }

            // VPSServer.TransactionReport(term.get_abort(), transactionValue);

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }
        finally{
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void executeNewOrder(DataInputStream in)throws Exception{
        NewOrderData data = new NewOrderData();
        // int parameter_len = in.readInt();
        int strlen = 0;
        QueueNode node = new QueueNode(TT_NEW_ORDER);

        strlen = in.readInt();
        byte[] buf = new byte[strlen];
        in.readFully(buf);
        String w_id_str = new String(buf, StandardCharsets.UTF_8);
        data.w_id = Integer.parseInt(w_id_str);
        
        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String d_id_str = new String(buf, StandardCharsets.UTF_8);
        data.d_id = Integer.parseInt(d_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String c_id_str = new String(buf, StandardCharsets.UTF_8);
        data.c_id = Integer.parseInt(c_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String o_ol_cnt_str = new String(buf, StandardCharsets.UTF_8);
        data.o_ol_cnt = Integer.parseInt(o_ol_cnt_str);
        
        for (int i = 0; i < data.o_ol_cnt; i++) {
            strlen = in.readInt();
            buf = new byte[strlen];
            in.readFully(buf);
            String ol_supply_w_id_str = new String(buf, StandardCharsets.UTF_8);
            data.ol_supply_w_id[i] = Integer.parseInt(ol_supply_w_id_str);

            strlen = in.readInt();
            buf = new byte[strlen];
            in.readFully(buf);
            String ol_i_id_str = new String(buf, StandardCharsets.UTF_8);
            data.ol_i_id[i] = Integer.parseInt(ol_i_id_str);

            strlen = in.readInt();
            buf = new byte[strlen];
            in.readFully(buf);
            String ol_quantity_str = new String(buf, StandardCharsets.UTF_8);
            data.ol_quantity[i] = Integer.parseInt(ol_quantity_str);
        }
        
        node.setNewOrderData(data);
        
        if(this.prio == 1){
            double context[] = VPSServer.getContext();

            int onehot[] = {0,0,0,0,0};
            onehot[0] = 1;

            modelInput.put("type_onehots",onehot);
            modelInput.put("inputdatas", List.of(data.w_id,data.d_id,data.c_id,-1,-1,-1));
            modelInput.put("items_quantity", data.ol_quantity);
            modelInput.put("items_id",data.ol_i_id);
            modelInput.put("values", 0);
            modelInput.put("contexts",context);
            

            myModel.setData(modelInput);
            long Thread_id = Thread.currentThread().threadId();
            String jsonpath = "/SSD00/lyb/test/VPS/thread"+String.valueOf(Thread_id)+".json";

            myModel.createJSON(jsonpath);
            long startTime = System.currentTimeMillis();
            double predvalue = myModel.startPython(jsonpath);
            long endTime = System.currentTimeMillis();
            VPSServer.updatePredictTime(endTime-startTime);
            node.setPredValue(predvalue);
        }
        VPSServer.requestQueue.enqueue(node);
        // term.setNewOrderData(data.w_id, data.d_id, data.c_id, data.o_ol_cnt, data.ol_supply_w_id, data.ol_i_id, data.ol_quantity);
        // term.executeNewOrder(log, db, rnd);
    }


    public void executePayment(DataInputStream in)throws Exception{
        QueueNode node = new QueueNode(TT_PAYMENT);
        
        
        if(this.prio == 1){
            double context[] = VPSServer.getContext();
            int onehot[] = {0,0,0,0,0};
            onehot[0] = 1;

            double[] items_quantity= new double[15];
            int [] item_id = new int [15];
            modelInput.put("type_onehots",onehot);
            modelInput.put("inputdatas",List.of(-1,-1,-1,-1,-1,-1));
            modelInput.put("items_quantity",items_quantity);
            modelInput.put("items_id",item_id);
            modelInput.put("values", 0);
            modelInput.put("contexts",context);
            myModel.setData(modelInput);
            long Thread_id = Thread.currentThread().threadId();
            String jsonpath = "/SSD00/lyb/test/VPS/thread"+String.valueOf(Thread_id)+".json";

            myModel.createJSON(jsonpath);
            long startTime = System.currentTimeMillis();
            double predvalue = myModel.startPython(jsonpath);
            long endTime = System.currentTimeMillis();
            VPSServer.updatePredictTime(endTime-startTime);
            node.setPredValue(predvalue);
        }
        VPSServer.requestQueue.enqueue(node);
        // term.execute(log, db, rnd);
        // this.transactionValue = term.getTransVal_real();
    }

    public void executeOrderStatus(DataInputStream in)throws Exception{
        OrderStatusData data = new OrderStatusData();
        QueueNode node = new QueueNode(TT_ORDER_STATUS);
        byte[] buf;
        int strlen;

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String w_id_str = new String(buf, StandardCharsets.UTF_8);
        data.w_id = Integer.parseInt(w_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String d_id_str = new String(buf, StandardCharsets.UTF_8);
        data.d_id = Integer.parseInt(d_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String c_id_str = new String(buf, StandardCharsets.UTF_8);
        data.c_id = Integer.parseInt(c_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        data.c_last = new String(buf, StandardCharsets.UTF_8);

        node.setOrderStatusData(data);
        // vector = embedding(node);
        // predict_value = model(vector);
        // node.setvalue = predict_value;
        double context[] = VPSServer.getContext();
        int c_last[]  = {-1,-1,-1};
        if(data.c_id==0){
            c_last = splitIntoThree(data.c_last);
        }

        
        if(this.prio == 1){
            int onehot[] = {0,0,0,0,0};
            onehot[0] = 1;

            double[] items_quantity= new double[15];
            int [] item_id = new int [15];

            modelInput.put("type_onehots",onehot);
            modelInput.put("inputdatas",List.of(data.w_id,data.d_id,data.c_id,c_last[0],c_last[1],c_last[2]));
            modelInput.put("items_quantity",items_quantity);
            modelInput.put("items_id",item_id);
            modelInput.put("values", 0);
            modelInput.put("contexts",context);

            myModel.setData(modelInput);
            long Thread_id = Thread.currentThread().threadId();
            String jsonpath = "/SSD00/lyb/test/VPS/thread"+String.valueOf(Thread_id)+".json";
            myModel.createJSON(jsonpath);
            long startTime = System.currentTimeMillis();
            double predvalue = myModel.startPython(jsonpath);
            long endTime = System.currentTimeMillis();
            VPSServer.updatePredictTime(endTime-startTime);
            node.setPredValue(predvalue);
        }
        VPSServer.requestQueue.enqueue(node);
        // term.setOrderStatusData(data.w_id, data.d_id, data.c_id, data.c_last);
        // term.executeOrderStatus(log, db);

    }

    public void executeStockLevel(DataInputStream in)throws Exception{
        StockLevelData data = new StockLevelData();
        QueueNode node = new QueueNode(TT_STOCK_LEVEL);
        byte[] buf;
        int strlen;

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String w_id_str = new String(buf, StandardCharsets.UTF_8);
        data.w_id = Integer.parseInt(w_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String d_id_str = new String(buf, StandardCharsets.UTF_8);
        data.d_id = Integer.parseInt(d_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String threshold_str = new String(buf, StandardCharsets.UTF_8);
        data.threshold = Integer.parseInt(threshold_str);

        node.setStockLevelData(data);
        if(this.prio == 1){
            double context[] = VPSServer.getContext();

            int onehot[] = {0,0,0,0,0};
            onehot[0] = 1;

            double[] items_quantity= new double[15];
            int [] item_id = new int [15];

            modelInput.put("type_onehots",onehot);
            modelInput.put("inputdatas",List.of(data.w_id,data.d_id,-1,-1,-1,-1));
            modelInput.put("items_quantity",items_quantity);
            modelInput.put("items_id",item_id);
            modelInput.put("values", 0);
            modelInput.put("contexts",context);

            myModel.setData(modelInput);
            long Thread_id = Thread.currentThread().threadId();
            String jsonpath = "/SSD00/lyb/test/VPS/thread"+String.valueOf(Thread_id)+".json";
            myModel.createJSON(jsonpath);
            long startTime = System.currentTimeMillis();
            double predvalue = myModel.startPython(jsonpath);
            long endTime = System.currentTimeMillis();
            VPSServer.updatePredictTime(endTime-startTime);
            node.setPredValue(predvalue);
        }
        VPSServer.requestQueue.enqueue(node);
        // term.setStockLevelData(data.w_id, data.d_id, data.threshold);
        // term.executeStockLevel(log, db);
    }

    public void executeDelivery(DataInputStream in)throws Exception{
        DeliveryData data = new DeliveryData();
        QueueNode node = new QueueNode(TT_DELIVERY);
        byte[] buf;
        int strlen;

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String w_id_str = new String(buf, StandardCharsets.UTF_8);
        data.w_id = Integer.parseInt(w_id_str);

        strlen = in.readInt();
        buf = new byte[strlen];
        in.readFully(buf);
        String o_carrier_id_str = new String(buf, StandardCharsets.UTF_8);
        data.o_carrier_id = Integer.parseInt(o_carrier_id_str);

        node.setDeliveryData(data);

        if(this.prio == 1){
            double context[] = VPSServer.getContext();int onehot[] = {0,0,0,0,0};
            onehot[0] = 1;

            double[] items_quantity= new double[15];
            int [] item_id = new int [15];

            modelInput.put("type_onehots",onehot);
            modelInput.put("inputdatas",List.of(data.w_id,-1,-1,-1,-1,-1));
            modelInput.put("items_quantity",items_quantity);
            modelInput.put("items_id",item_id);
            modelInput.put("values", 0);
            modelInput.put("contexts",context);

            myModel.setData(modelInput);
            long Thread_id = Thread.currentThread().threadId();
            String jsonpath = "/SSD00/lyb/test/VPS/thread"+String.valueOf(Thread_id)+".json";
            myModel.createJSON(jsonpath);
            long startTime = System.currentTimeMillis();
            double predvalue = myModel.startPython(jsonpath);
            long endTime = System.currentTimeMillis();
            VPSServer.updatePredictTime(endTime-startTime);
            node.setPredValue(predvalue);
        }
        VPSServer.requestQueue.enqueue(node);
        // term.setDeliveryData(data.w_id, data.o_carrier_id);
        // term.executeDelivery(log, db);
    }   


    public class NewOrderData {
        /* terminal input data */
        public int w_id;
        public int d_id;
        public int c_id;
        public int o_ol_cnt;
        public int ol_supply_w_id[] = new int[15];
        public int ol_i_id[] = new int[15];
        public int ol_quantity[] = new int[15];
    }

    public class PaymentData {
        /* terminal input data */
        public int w_id;
        public int d_id;
        public int c_id;
        public int c_d_id;
        public int c_w_id;
        public String c_last;
        public double h_amount;
    }
    public class StockLevelData {
		/* terminal input data */
		public int w_id;
		public int d_id;
		public int threshold;
		public int d_next_o_id;
		public double ol_amount;
		public int ol_quantity;
		public int[] s_i_id = new int[101]; 
		public Map<Integer,Double>reStock;
    }

    public class OrderStatusData {
		/* terminal input data */
		public int w_id;
		public int d_id;
		public int c_id;
		public String c_last;
    }

    public class DeliveryData {
		/* terminal input data */
		public int w_id;
		public int o_carrier_id;
    }

    public static int[] splitIntoThree(String s) {
        s = s.toUpperCase();   // 保守起见

        int n = s.length();
        if(n == 0){
            return new int[]{-1, -1, -1};
        }

        for (int i = 0; i < C_LAST_TOKEN.length; i++) {
            String t1 = C_LAST_TOKEN[i];
            if (!s.startsWith(t1)) continue;

            for (int j = 0; j < C_LAST_TOKEN.length; j++) {
                String t2 = C_LAST_TOKEN[j];
                int pos2 = t1.length();
                if (pos2 + t2.length() >= n) continue;

                if (!s.startsWith(t2, pos2)) continue;

                String t3 = s.substring(pos2 + t2.length());
                Integer k = TOKEN_INDEX.get(t3);
                if (k != null) {
                    return new int[]{i, j, k};
                }
            }
        }
        // 理论上不会走到这里
        throw new IllegalStateException("No valid 3-token split found for: " + s);
    }
}
