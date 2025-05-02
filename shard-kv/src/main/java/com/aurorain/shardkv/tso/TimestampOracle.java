package com.aurorain.shardkv.tso;

import java.util.concurrent.atomic.AtomicLong;

public class TimestampOracle {

    private static final int PHYSICAL_SHIFT_BITS = 18;
    private static final long LOGICAL_MASK = ~(-1L << PHYSICAL_SHIFT_BITS); // 0x3FFFF

    private final AtomicLong lastTimestamp = new AtomicLong(0);

    public synchronized long getTimestamp() {
        long now = System.currentTimeMillis();

        long lastTs = lastTimestamp.get();
        if (now > physicalTime(lastTs)) {
            lastTimestamp.set(now << PHYSICAL_SHIFT_BITS);
        } else if (now == physicalTime(lastTs)) {
            long logical = (lastTs & LOGICAL_MASK) + 1;
            if (logical > LOGICAL_MASK) {
                // 达到最大逻辑序号，等待下一个物理时间片
                waitForNextMillis(lastTs);
                now = physicalTime(lastTs) + 1;
                lastTimestamp.set(now << PHYSICAL_SHIFT_BITS);
            } else {
                lastTimestamp.set((now << PHYSICAL_SHIFT_BITS) | logical);
            }
        } else {
            throw new RuntimeException("时钟回拨");
        }

        return lastTimestamp.get();
    }

    private void waitForNextMillis(long lastTimestamp) {
        long now = System.currentTimeMillis();
        while (now <= physicalTime(lastTimestamp)) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            now = System.currentTimeMillis();
        }
    }

    // 提取物理时间部分
    public static long physicalTime(long ts) {
        return ts >> PHYSICAL_SHIFT_BITS;
    }

    // 提取逻辑序列号部分
    public static long logicalSequence(long ts) {
        return ts & LOGICAL_MASK;
    }
}