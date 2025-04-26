package com.aurorain.shardkv.transaction;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.util.List;

public class Latches {

    // Map to store conditions for each key
    private final Map<String, Condition> latchMap;
    // Lock to guard the latchMap
    private final Lock latchGuard;

    public Latches() {
        this.latchMap = new HashMap<>();
        this.latchGuard = new ReentrantLock();
    }

    // Acquire latches for the given keys
    public boolean acquireLatches(List<String> keysToLatch) throws InterruptedException {
        latchGuard.lock();
        try {
            // Check if any of the keys are already locked
            for (String key : keysToLatch) {
                while (latchMap.containsKey(key)) {
                    // Wait for the key to be released
                    Condition condition = latchMap.get(key);
                    condition.await();
                }
            }

            // All latches are available, lock them all
            for (String key : keysToLatch) {
                latchMap.put(key, latchGuard.newCondition());
            }

            return true;
        } finally {
            latchGuard.unlock();
        }
    }

    // Release latches for the given keys
    public void releaseLatches(List<String> keysToUnlatch) {
        latchGuard.lock();
        try {
            for (String key : keysToUnlatch) {
                Condition condition = latchMap.remove(key);
                if (condition != null) {
                    condition.signalAll(); // Wake up all waiting threads
                }
            }
        } finally {
            latchGuard.unlock();
        }
    }

    // Wait for latches to be available
    public void waitForLatches(List<String> keysToLatch) throws InterruptedException {
        while (!acquireLatches(keysToLatch)) {
            // Retry until latches are acquired
        }
    }



}
