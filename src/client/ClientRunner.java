package client;

import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Map;

public class ClientRunner implements Runnable{
    private int port;
    private String host;
    private Socket server;
    private int transactionType;
    public final static int TT_NEW_ORDER = 0,
                            TT_PAYMENT = 1,
                            TT_ORDER_STATUS = 2,
                            TT_DELIVERY = 3,
                            TT_STOCK_LEVEL = 4;
    private String[] parameters;
    private int parameter_len;
    private jTPCCRandom rnd;
    private int max_warehouse;

    

    public ClientRunner(String host, int port,int transactionType,int max_warehouse,jTPCCRandom rnd){
        this.host = host;
        this.port = port;
        this.transactionType = transactionType;
        this.max_warehouse = max_warehouse;
        this.rnd = rnd;
    }

    @Override
    public void run() {
        try{
            server = new Socket(host, port);
            DataOutputStream dos = new DataOutputStream(server.getOutputStream());

            switch (transactionType) {
                case TT_NEW_ORDER:
                    generateNewOrderTransaction();
                    break;
                case TT_PAYMENT:
                    generatePaymentTransaction();
                    break;
                case TT_ORDER_STATUS:
                    generateOrderStatusTransaction();
                    break;
                case TT_STOCK_LEVEL:
                    generateStockLevelTransaction();
                    break;
                case TT_DELIVERY:
                    generateDeliveryTransaction();
                    break;
                default:
                    break;
            }

            dos.writeInt(transactionType);

            // Ensure parameters is not null
            if (this.parameters == null) {
                this.parameters = new String[0];
                this.parameter_len = 0;
            }

            // write actual number of parameters
            // dos.writeInt(this.parameters.length);

            for (int i = 0; i < this.parameters.length; i++) {
                String param = this.parameters[i];
                if (param == null) param = "";
                byte[] strBytes = param.getBytes("UTF-8");
                dos.writeInt(strBytes.length); // 写入字符串长度
                dos.write(strBytes);           // 写入字符串内容
            }
            dos.flush();
            server.close();
        }
        catch(java.net.ConnectException e){
            
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    private void generateNewOrderTransaction(){
        NewOrderData newOrder = new NewOrderData();
        newOrder.w_id = rnd.nextInt(1, max_warehouse);
        newOrder.d_id = rnd.nextInt(1, 10);
        newOrder.c_id = rnd.getCustomerID();
        newOrder.o_ol_cnt = rnd.nextInt(5, 15);

        for (int i = 0; i < newOrder.o_ol_cnt; i++) {
            // choose item id using non-uniform distribution
            newOrder.ol_i_id[i] = rnd.getItemID();
            // 99% local supply, 1% remote as in jTPCCTData
            if (rnd.nextInt(1, 100) <= 99) {
                newOrder.ol_supply_w_id[i] = newOrder.w_id;
            } else {
                // fallback to a random warehouse in [1..10]
                newOrder.ol_supply_w_id[i] = rnd.nextInt(1, 10);
            }
            newOrder.ol_quantity[i] = rnd.nextInt(1, 10);
        }

        // Flatten parameters: w_id,d_id,c_id,o_ol_cnt, then for each order line: supply_w_id,item_id,quantity
        this.parameter_len = 4 + 3 * newOrder.o_ol_cnt;
        this.parameters = new String[this.parameter_len];
        int idx = 0;
        parameters[idx++] = Integer.toString(newOrder.w_id);
        parameters[idx++] = Integer.toString(newOrder.d_id);
        parameters[idx++] = Integer.toString(newOrder.c_id);
        parameters[idx++] = Integer.toString(newOrder.o_ol_cnt);
        for (int i = 0; i < newOrder.o_ol_cnt; i++) {
            parameters[idx++] = Integer.toString(newOrder.ol_supply_w_id[i]);
            parameters[idx++] = Integer.toString(newOrder.ol_i_id[i]);
            parameters[idx++] = Integer.toString(newOrder.ol_quantity[i]);
        }
    }

    private void generatePaymentTransaction(){
        this.parameter_len = 0;
        this.parameters = new String[1];
        parameters[0] = "null";
    }

    private void generateOrderStatusTransaction(){
        this.parameter_len = 4;
        this.parameters = new String[4];
        OrderStatusData orderStatus = new OrderStatusData();
        orderStatus.w_id = rnd.nextInt(1, max_warehouse);
		orderStatus.d_id = rnd.nextInt(1, 10);
		if (rnd.nextInt(1, 100) <= 60) {
			orderStatus.c_id = 0;
			orderStatus.c_last = rnd.getCLast();
		} else {
			orderStatus.c_id = rnd.getCustomerID();
			orderStatus.c_last = null;
		}
        parameters[0] = Integer.toString(orderStatus.w_id);
        parameters[1] = Integer.toString(orderStatus.d_id);
        parameters[2] = Integer.toString(orderStatus.c_id);
        parameters[3] = orderStatus.c_last;        
    }

    private void generateStockLevelTransaction(){
        StockLevelData stock = new StockLevelData();
        stock.w_id = rnd.nextInt(1, max_warehouse);
        stock.d_id = rnd.nextInt(1, 10);
        stock.threshold = rnd.nextInt(10, 20);

        // Flatten: w_id,d_id,threshold
        this.parameter_len = 3;
        this.parameters = new String[this.parameter_len];
        parameters[0] = Integer.toString(stock.w_id);
        parameters[1] = Integer.toString(stock.d_id);
        parameters[2] = Integer.toString(stock.threshold);
    }

    private void generateDeliveryTransaction(){
        DeliveryData delivery = new DeliveryData();
        delivery.w_id = rnd.nextInt(1, max_warehouse);
		delivery.o_carrier_id = rnd.nextInt(1, 10);
        parameter_len = 2;
        parameters = new String[2];
        parameters[0] = Integer.toString(delivery.w_id);
        parameters[1] = Integer.toString(delivery.o_carrier_id);
    }

    private class NewOrderData {
        /* terminal input data */
        public int w_id;
        public int d_id;
        public int c_id;
        public int o_ol_cnt;
        public int ol_supply_w_id[] = new int[15];
        public int ol_i_id[] = new int[15];
        public int ol_quantity[] = new int[15];
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
    }

    private class OrderStatusData {
		/* terminal input data */
		public int w_id;
		public int d_id;
		public int c_id;
		public String c_last;
    }

    private class DeliveryData {
		/* terminal input data */
		public int w_id;
		public int o_carrier_id;
    }
}

