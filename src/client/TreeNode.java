package client;

import java.util.*;
import java.sql.*;

public class TreeNode{
    private String sqlstring;
    private int transType;
    private Double transVal;
    private long generateTime;
    private int priority;
    
    public TreeNode(String sql, int transType, Double transVal, long generateTime,int priority){
        this.sqlstring = sql;
        this.transType = transType;
        this.transVal = transVal;
        this.generateTime = generateTime;
        this.priority = priority;
    }

    public String getSQL(){
        return sqlstring;
    }

    public int getTransType(){
        return transType;
    }

    public Double getTransVal(){
        return transVal;
    }

    public long getGenerateTime(){
        return generateTime;
    }

    public int getPriority(){
        return priority;
    }
}
