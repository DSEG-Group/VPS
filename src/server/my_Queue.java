// package server;


// public class my_Queue {
//     private int max_len;
//     private int head;
//     private int tail;
//     private QueueNode[] nodes;

//     public my_Queue(int capacity) {
//         this.max_len = capacity;
//         this.head = 0;
//         this.tail = 0;
//         this.nodes = new QueueNode[capacity];
//     }

//     public synchronized boolean isFull() {
//         return (tail + 1) % max_len == head;
//     }

//     public synchronized boolean isEmpty() {
//         return head == tail;
//     }

//     public synchronized void enqueue(QueueNode node) throws InterruptedException {
//         while (isFull()) {
//             wait();
//         }
//         nodes[tail] = node;
//         tail = (tail + 1) % max_len;
//         notifyAll();
//     }

//     public synchronized QueueNode dequeue() throws InterruptedException {
//         while (isEmpty()) {
//             wait();
//         }
//         QueueNode node = nodes[head];
//         head = (head + 1) % max_len;
//         notifyAll();
//         return node;
//     }

// }

package server;

import java.util.PriorityQueue;
import java.util.Comparator;

public class my_Queue {

    private final int max_len;
    private final PriorityQueue<QueueNode> queue;

    public my_Queue(int capacity) {
        this.max_len = capacity;

        // 优先级规则：pred_value 大的先出队
        this.queue = new PriorityQueue<>(
            Comparator.comparingDouble(QueueNode::getPred_value).reversed()
        );
    }

    public synchronized boolean isFull() {
        return queue.size() >= max_len;
    }

    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    public synchronized void enqueue(QueueNode node) throws InterruptedException {
        while (isFull()) {
            wait();
        }
        queue.offer(node);
        notifyAll();
    }

    public synchronized QueueNode dequeue() throws InterruptedException {
        while (isEmpty()) {
            wait();
        }
        QueueNode node = queue.poll();
        notifyAll();
        return node;
    }
}