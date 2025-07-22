package pre;

import OSCollector.OSCollector;
import pre.TreeNode;

import org.apache.log4j.*;
import org.firebirdsql.jdbc.parser.JaybirdSqlParser.substringFunction_return;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import java.text.*;
import java.util.stream.Stream;
import org.json.JSONObject;

public class preprocessing{
    private Heap heap;
    static private String readfilepath;
    static private BufferedReader filereader;
    static private String writerfilepath;
    static private BufferedWriter filewriter;

    public final static int TT_NEW_ORDER = 0,
	TT_PAYMENT = 1,
	TT_ORDER_STATUS = 2,
	TT_STOCK_LEVEL = 3,
	TT_DELIVERY = 4,
	TT_DELIVERY_BG = 5,
	TT_NONE = 6,
	TT_DONE = 7;

    public preprocessing(){
        heap = new Heap();
        readfilepath = "/home/lyb/vps_benchmark/run/standard_data/pg_result.json";
        writerfilepath = "/home/lyb/vps_benchmark/run/standard_data/after_sort.json";
    }

    public void main(){
        try {
            filereader = new BufferedReader(new FileReader(readfilepath));
            filewriter = new BufferedWriter(new FileWriter(writerfilepath));
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(1);
        }
        String SQLString = "";
        SQLString = readJsonLine();
        while(SQLString != ""){
            int head = SQLString.indexOf(":")+1;
			int tail = SQLString.length()-1;
            String subSQLString = "";
			subSQLString  = SQLString.substring(head, tail);
			JSONObject SQLJson = new JSONObject(subSQLString);
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
			TreeNode node = new TreeNode(QueryString, transType, transVal, generateTime, priority);
			heap.add(node);
            SQLString = readJsonLine();
        }
        writeJsonLine();
    }

    private void writeJsonLine(){
        TreeNode node;
        String SQLString;
        double transVal;
        int transType;
        int priority;
        long generateTime; 
        String WriteBuffer;
        int k = 0;
        try{
            while(heap.getSize()>0){
                k = 0;
                WriteBuffer = "";
                node = heap.pop();
                SQLString  = node.getSQL();
                transVal = node.getTransVal();
                priority = node.getPriority();
                transType = node.getTransType();
                generateTime = node.getGenerateTime();
                WriteBuffer+=k+":\n{";
                WriteBuffer+="\"sql\":\""+SQLString+"\",\n";
                WriteBuffer+="\"value\":\""+transVal+"\",\n";
                WriteBuffer+="\"priority\":\""+priority+"\",\n";
                WriteBuffer+="\"generateTime\":\""+generateTime+"\",\n";
                switch(transType){
                    case TT_NEW_ORDER:
                        WriteBuffer+="\"transType\":\"New-Order\"},\n";
                        break;
                    case TT_PAYMENT:
                        WriteBuffer+="\"transType\":\"Payment\"},\n";
                        break;
                    case TT_DELIVERY:
                        WriteBuffer+="\"transType\":\"Delivery\"},\n";
                        break;
                    case TT_ORDER_STATUS:
                        WriteBuffer+="\"transType\":\"Order-Status\"},\n";
                        break;
                    case TT_STOCK_LEVEL:
                        WriteBuffer+="\"transType\":\"Stock-Level\"},\n";
                        break;    
                }
                filewriter.write(WriteBuffer);
                k++;
            }
            filewriter.close();;
        }
        catch(IOException e){
            System.out.println(e.getMessage());
            System.exit(1);
		}
    }

    private String readJsonLine(){
		String JsonLine = "";
		String tmpLine = "";
		try{
			while((tmpLine = filereader.readLine())!= null){
				String FirstSeven = tmpLine.length() >= 11 ? tmpLine.substring(0,11) : tmpLine;
				JsonLine+=tmpLine;
				if(FirstSeven.equals("\"transType\"")){
					JsonLine = JsonLine.substring(0,JsonLine.length());//去除“，”
					return JsonLine;
				}
			}
		}
		catch(IOException e){
            System.out.println(e.getMessage());
            System.exit(1);
		}
		return JsonLine;
	}

}


