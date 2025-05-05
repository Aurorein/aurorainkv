package com.aurorain.shardkv.transaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

public class Latches {
    // Key -> WaitGroup 的映射
    private final Map<String, CountDownLatch> latchMap;
    // 保护 latchMap 的全局锁
    private final ReentrantLock latchGuard;
    // 测试用的验证函数
//    private ValidationFunction validation;
//
//    public interface ValidationFunction {
//        void validate(MvccTxn txn, List<byte[]> keys);
//    }

    public Latches() {
        this.latchMap = new HashMap<>();
        this.latchGuard = new ReentrantLock();
    }

    /**
     * 尝试获取所有 key 的锁
     * @return null 表示成功，否则返回需要等待的 CountDownLatch
     */
    public CountDownLatch acquireLatches(List<String> keysToLatch) {
        latchGuard.lock();
        try {
            // 检查是否有 key 已被锁定
            for (String key : keysToLatch) {
                if (latchMap.containsKey(key)) {
                    return latchMap.get(key); // 返回需要等待的锁
                }
            }

            // 所有 key 可用，创建新的 CountDownLatch 并锁定
            CountDownLatch wg = new CountDownLatch(1);
            for (String key : keysToLatch) {
                latchMap.put(key, wg);
            }
            return null;
        } finally {
            latchGuard.unlock();
        }
    }

    /**
     * 释放所有 key 的锁
     */
    public void releaseLatches(List<String> keysToUnlatch) {
        latchGuard.lock();
        try {
            boolean first = true;
            for (String key : keysToUnlatch) {
                if (first) {
                    CountDownLatch wg = latchMap.get(key);
                    if (wg != null) wg.countDown(); // 唤醒等待线程
                    first = false;
                }
                latchMap.remove(key);
            }
        } finally {
            latchGuard.unlock();
        }
    }

    /**
     * 阻塞直到获取所有 key 的锁
     */
    public void waitForLatches(List<String> keysToLatch) throws InterruptedException {
        while (true) {
            CountDownLatch wg = acquireLatches(keysToLatch);
            if (wg == null) return;
            wg.await(); // 阻塞等待锁释放
        }
    }

    // 设置验证函数（测试用）
//    public void setValidation(ValidationFunction validation) {
//        this.validation = validation;
//    }
//
//    // 执行验证（测试用）
//    public void validate(MvccTxn txn, List<byte[]> latchedKeys) {
//        if (validation != null) {
//            validation.validate(txn, latchedKeys);
//        }
//    }
}