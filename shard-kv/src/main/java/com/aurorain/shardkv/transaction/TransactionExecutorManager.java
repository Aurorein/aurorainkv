package com.aurorain.shardkv.transaction;

import com.aurorain.shardkv.Client;
import com.aurorain.shardkv.store.RocksDBEntry;
import com.aurorain.shardkv.tso.TimestampOracle;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class TransactionExecutorManager {

    public final int RETRY_ROUND = 3;
    private Client client;
    TimestampOracle timestampOracle;
    public long txnId;
    List<String> logs;
    private CountDownLatch countDownLatch;

    public TransactionExecutorManager(Client client, TimestampOracle timestampOracle, long txnId) {
        this.client = client;
        this.timestampOracle = timestampOracle;
        this.txnId = txnId;
        this.logs = Collections.synchronizedList(new ArrayList<>());
    }

    public TransactionExecutorManager(Client client, TimestampOracle timestampOracle, long txnId, List<String> logs, CountDownLatch countDownLatch) {
        this.client = client;
        this.timestampOracle = timestampOracle;
        this.txnId = txnId;
        this.logs = logs;
        this.countDownLatch = countDownLatch;
    }


    /**
     * startTS是第一次执行事务时的startTs，如果重试的话则重新通过TSO获取startTs
     * @param primary
     * @param entries
     * @param startTS
     * @return
     */
    public boolean execute2PC(String primary, List<RocksDBEntry> entries, long startTS) throws Exception {
        boolean success = false;
        TwoPhaseCommitter twoPhaseCommitter = new TwoPhaseCommitter(startTS, primary, client, entries, timestampOracle, txnId, logs);
        success = twoPhaseCommitter.execute();
        int retry = 1;
        while(retry < RETRY_ROUND && !success) {
            long timesTamp = timestampOracle.getTimestamp();
            TwoPhaseCommitter twoPhaseCommitter1 = new TwoPhaseCommitter(timesTamp, primary, client, entries, timestampOracle, txnId, logs);
            success = twoPhaseCommitter1.execute();
            if(!success) {
                Thread.sleep(500 * (retry + 1));
            }else {
                while(twoPhaseCommitter.isDealing()) {
                    Thread.sleep(200);
                }
            }
        }
        if(countDownLatch != null) countDownLatch.countDown();
        return success;
    }

    /**
     * 第一次执行事务的时候从TSO获取时间戳
     * @param primary
     * @param entries
     * @return
     */
    public boolean execute2PC(String primary, List<RocksDBEntry> entries) throws Exception {
        boolean success = false;
        int retry = 0;
        while(retry < RETRY_ROUND && !success) {
            long timesTamp = timestampOracle.getTimestamp();
            TwoPhaseCommitter twoPhaseCommitter = new TwoPhaseCommitter(timesTamp, primary, client, entries, timestampOracle, txnId, logs);
            success = twoPhaseCommitter.execute();
            if(!success) {
                Thread.sleep(500 * (retry + 1));
            } else {
                while(twoPhaseCommitter.isDealing()) {
                    Thread.sleep(200);
                }
            }
        }
        if(countDownLatch != null) countDownLatch.countDown();
        return success;
    }

    /**
     * startTS是第一次执行事务时的startTs, commitTs是第一次执行commit的Ts，如果重试的话则重新通过TSO获取startTs
     * @param primary
     * @param entries
     * @param startTS
     * @param commitTS
     * @return
     */
    public boolean execute2PC(String primary, List<RocksDBEntry> entries, long startTS, long commitTS) throws Exception {
        if(startTS >= commitTS) {
            log.error("startTs必须小于等于commitTs");
            return false;
        }
        boolean success = false;
        TwoPhaseCommitter twoPhaseCommitter = new TwoPhaseCommitter(startTS, primary, client, entries, timestampOracle, commitTS, txnId, logs);
        success = twoPhaseCommitter.execute();
        int retry = 1;
        while(retry < RETRY_ROUND && !success) {
            long timesTamp = timestampOracle.getTimestamp();
            TwoPhaseCommitter twoPhaseCommitter1 = new TwoPhaseCommitter(timesTamp, primary, client, entries, timestampOracle, txnId, logs);
            success = twoPhaseCommitter1.execute();
            if(!success) {
                Thread.sleep(500 * (retry + 1));
            }else {
                while(twoPhaseCommitter.isDealing()) {
                    Thread.sleep(200);
                }
            }
        }
        if(countDownLatch != null) countDownLatch.countDown();
        return success;
    }

    /**
     * 制造client在preWrite阶段crash的情况
     * @param primary
     * @param entries
     * @return
     * @throws Exception
     */
    public boolean crash(String primary, List<RocksDBEntry> entries) throws Exception {
        boolean success = false;
        int retry = 0;
        while(retry < RETRY_ROUND && !success) {
            long timesTamp = timestampOracle.getTimestamp();
            TwoPhaseCommitter twoPhaseCommitter = new TwoPhaseCommitter(timesTamp, primary, client, entries, timestampOracle, txnId, logs);
            log("事务 {} 开始执行preWrite;startTs = {}", txnId, timesTamp);
            twoPhaseCommitter.prewrite();
            success = !twoPhaseCommitter.isRollback();
            if(!success) {
                Thread.sleep(500 * (retry + 1));
            } else {
                while(twoPhaseCommitter.isDealing()) {
                    Thread.sleep(200);
                }
            }
        }
        if(success) log("事务 {} 发生crash;", txnId);
        if(countDownLatch != null) countDownLatch.countDown();
        return success;
    }

    /**
     * 获取key的值
     * @param startTs
     * @param key
     * @return
     * @throws Exception
     */
    public String get(long startTs, String key) throws Exception {
        TwoPhaseCommitter twoPhaseCommitter = new TwoPhaseCommitter(startTs, null, client, null, timestampOracle, txnId, logs);
        return twoPhaseCommitter.get(key);
    }

    /**
     * 传入特定的startTs来获取key
     * @param key
     * @return
     * @throws Exception
     */
    public String get(String key) throws Exception {
        long startTs = timestampOracle.getTimestamp();
        TwoPhaseCommitter twoPhaseCommitter = new TwoPhaseCommitter(startTs, null, client, null, timestampOracle, txnId, logs);
        return twoPhaseCommitter.get(key);
    }

    private void log(String format, Object... args) {
        String message;
        try {
            message = String.format(format.replace("{}", "%s"), args);
        } catch (Exception e) {
            System.err.println("日志格式化失败: " + e.getMessage());
            return;
        }
        log.info(message);
        logs.add(message);
    }

    public List<String> logs() {
        return logs;
    }
}
