package pre;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.json.JSONObject;

import client.jTPCC;
import client.jTPCCConfig;
import client.jTPCCConnection;
import client.jTPCCRandom;
import client.jTPCCTData;
import client.jTPCCTerminal;

import client.TreeNode;
import client.Heap;

public class workerthread implements jTPCCConfig, Runnable{

    public  caculate_offline_vps parent;
    static private int executeTime[] = {8,16,141,10,1};

	private double transVal;//change 11.13
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
	private int epoch = 0;

	private Map<Integer,Double> reStock_item = new HashMap<>();

	static int TPS = 88;
	static long max_time = 60000;

    long terminalStartTime = 0;
    long transactionEnd = 0;

    jTPCCConnection db = null;
    int                 dbType = 0;

	public final static int HEAP_MAX_SIZE = 10000;
	public final static int TT_NEW_ORDER = 0,
	TT_PAYMENT = 1,
	TT_STOCK_LEVEL = 2,
	TT_DELIVERY = 3,
	TT_ORDER_STATUS = 4;


    private String file_path;

    public workerthread(boolean isReadJson, String file_path,caculate_offline_vps parent){
        this.isReadJson = isReadJson;
        this.file_path = file_path;
        this.parent = parent;

    }

    public void run()
        {
        if(!isReadJson){
            if(!standardSQL){
                if(isHeap){
                    executeHeapSQL();
                }
            }
        }
        else{
            HeapReadJson();
        }

    }


    private void HeapReadJson(){
		String SQLString = parent.readJsonLine();
		boolean stopRunning = false;
		String writerfilepath = "/home/lyb/vps_benchmark/run/standard_data/my_test.csv";
		BufferedWriter filewriter = null;
		try {
			filewriter = new BufferedWriter(new FileWriter(writerfilepath));
			String buffer = "generateTime,transValue\n";
			filewriter.write(buffer);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		while(SQLString != ""&&!stopRunning){

			int Current_size = parent.tree.getSize();
			// if(Current_size>=HEAP_MAX_SIZE){// busy wait
			// 	if(stopRunningSignal) {
			// 		stopRunning = true;
			// 		// break;
			// 	}
			// 	// try{
			// 	// 	Thread.sleep(1);
			// 	// }
			// 	// catch(InterruptedException ie){
			// 	// 	ie.printStackTrace();
			// 	// }
			// 	continue;
				
			// }
			String writerbuffer;
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
			writerbuffer = String.valueOf(generateTime);
			writerbuffer += ',';
			writerbuffer += String.valueOf(transVal);
			writerbuffer += "\n";
			try {
				filewriter.write(writerbuffer);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			

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

			// while(timeDelta < generateTime){
			// 	CurrentTime = System.currentTimeMillis();
			// 	timeDelta = CurrentTime - parent.getSessionStart();
			// }//busy wait

			// TreeNode node = new TreeNode(QueryString, transType, transVal,generateTime,priority);
			// parent.tree.add(node);
			SQLString = parent.readJsonLine();
			// try{
			// 	Thread.sleep(10);
			// }
			// catch(InterruptedException ie){
			// 	ie.printStackTrace();
			// }
			// if(stopRunningSignal) {
			// 	stopRunning = true;
			// 	// break;
			// }
		}
		// parent.tree.setIsAllLoad(true);
		try {
			filewriter.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

private void executeHeapSQL(){
        try{
            String sqlString;
            double transVal;
            int transType;
            boolean stopRunning = false;
            long generateTime;
            int priority;
			int count = 0;
			long last_time = System.currentTimeMillis();
			long start_time = System.currentTimeMillis();
			long now_time = System.currentTimeMillis();
			long time_Detla = now_time-start_time;
			double valueCount = 0;
            while(time_Detla<max_time){
                if(parent.tree.getIsAllLoad()&&parent.tree.getSize() == 0){
                    break;
                }
                TreeNode node = parent.tree.pop();
                if(node == null){
                    Thread.sleep(1);
                    continue;
                }
                else{
					count++;
                    sqlString = node.getSQL();
                    transVal = node.getTransVal();
                    transType = node.getTransType();
                    generateTime = node.getGenerateTime();
                    priority = node.getPriority();
					valueCount+=transVal;
					if(count>=TPS){
						count = 0;
						now_time = System.currentTimeMillis();
						while(now_time-last_time<1000){
							Thread.sleep(100);
							now_time = System.currentTimeMillis();
						}
						last_time = now_time;
					}

                }
				now_time = System.currentTimeMillis();
				time_Detla = now_time-start_time;
				
            }
			System.out.println(valueCount/(max_time/1000));
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
