package client;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCache <K, V> extends LinkedHashMap<K, V>{
    private final int capacity;

    public LRUCache(int capacity) {
        // true = accessOrder（访问顺序）
        super(capacity, 0.75f, true);
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }

    public void printCache() {
        System.out.println("LRU Cache: " + super.toString());
    }

    public boolean isFull(){
        return size() == capacity; 
    }
}
