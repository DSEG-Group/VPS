
package server;

public class QueueNode {
    private int transactionType;
    private ServerRuner.NewOrderData MewOrderData;
    private ServerRuner.PaymentData paymentData;
    private ServerRuner.StockLevelData stockLevelData;
    private ServerRuner.OrderStatusData orderStatusData;
    private ServerRuner.DeliveryData deliveryData;
    private double pre_Value = 0;

    public QueueNode(int transactionType) {
        this.transactionType = transactionType;
    }

    public void setNewOrderData(ServerRuner.NewOrderData data) {
        this.MewOrderData = data;
    }

    public void setPaymentData(ServerRuner.PaymentData data) {
        this.paymentData = data;
    }

    public void setStockLevelData(ServerRuner.StockLevelData data) {
        this.stockLevelData = data;
    }

    public void setOrderStatusData(ServerRuner.OrderStatusData data) {
        this.orderStatusData = data;
    }

    public void setDeliveryData(ServerRuner.DeliveryData data) {
        this.deliveryData = data;
    }

    public void setPredValue(double predValue){
        this.pre_Value = predValue;
    }

    public int getTransactionType() {
        return transactionType;
    }

    public double getPred_value(){
        return this.pre_Value;
    }

    public ServerRuner.NewOrderData getNewOrderData() {
        return MewOrderData;
    }

    public ServerRuner.PaymentData getPaymentData() {
        return paymentData;
    }

    public ServerRuner.StockLevelData getStockLevelData() {
        return stockLevelData;
    }

    public ServerRuner.OrderStatusData getOrderStatusData() {
        return orderStatusData;
    }

    public ServerRuner.DeliveryData getDeliveryData() {
        return deliveryData;
    }
}