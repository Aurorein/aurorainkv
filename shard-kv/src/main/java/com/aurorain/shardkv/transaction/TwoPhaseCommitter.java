package com.aurorain.shardkv.transaction;

import com.aurorain.shardkv.Client;
import com.aurorain.shardkv.constant.TxnConstants;
import com.aurorain.shardkv.model.dto.*;
import com.aurorain.shardkv.store.RocksDBEntry;
import com.aurorain.shardkv.tso.TimestampOracle;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class TwoPhaseCommitter {

    public final int RETRY_ROUND = 3;
    private TimestampOracle timestampOracle;
    private final long startTS;
    private long commitTs;
    private final String primary;
    private final Client client;
    private boolean committed = false;
    private boolean rollback = false;
    private List<RocksDBEntry> entries;
    private long txnId;
    private List<String> logs;
    // commit的secondaries由公共线程异步提交
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAX_POOL_SIZE = 16;
    private static final long KEEP_ALIVE_TIME = 60L; // 单位秒

    private static final int MAX_RETRY = 3;
    private static final long RETRY_INTERVAL_MS = 500;

    private static final ThreadPoolExecutor asyncSecondaryCommitPool = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100), // 设置队列容量限制
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满后由调用线程自己执行
    );
    private boolean dealing= false;

    public TwoPhaseCommitter(long startTS, String primary, Client client, List<RocksDBEntry> entries, TimestampOracle timestampOracle, long txnId, List<String> logs) {
        this.startTS = startTS;
        this.primary = primary;
        this.client = client;
        this.entries = entries;
        this.timestampOracle = timestampOracle;
        this.commitTs = 0;
        this.txnId = txnId;
        this.logs = logs;
    }

    public TwoPhaseCommitter(long startTS, String primary, Client client, List<RocksDBEntry> entries, TimestampOracle timestampOracle, long commitTs, long txnId, List<String> logs) {
        this.startTS = startTS;
        this.primary = primary;
        this.client = client;
        this.entries = entries;
        this.timestampOracle = timestampOracle;
        this.commitTs = commitTs;
        this.txnId = txnId;
        this.logs = logs;
    }

    /**
     * 执行两阶段提交，返回false代表rollback，true代表committed
     * @return
     * @throws Exception
     */
    public boolean execute() throws Exception {
        log("事务 {} 开始执行preWrite;startTs = {}", txnId, startTS);
        prewrite();
        log("事务 {} 完成preWrite;是否回滚: {}", txnId, rollback);

        if(rollback) {
            return false;
        }

        commit();
        log("事务 {} 结束commit;是否提交成功: {}", txnId, committed);
        return committed;
    }

    public void prewrite() throws Exception {
        PreWriteArgs args = new PreWriteArgs();
        args.setEntries(entries);
        args.setStartTs(startTS);
        args.setPrimary(primary);
        args.setLockTtl(1000); // 合适的？

        while(true) {
            CommandResponse response = client.kvPreWrite("key", args);
            long retryTime = 0;
            List<KeyError> keyErrors = response.getKeyErrors();

            if(keyErrors == null || keyErrors.size() == 0) {
                return;
            }
            log("事务 {} 发现存在Lock或者Write冲突; 冲突: {}", txnId, keyErrors);
            retryTime = handleKeyErrors(response.getKeyErrors());
            if(rollback) {
                return;
            }
            if(retryTime > 0) {
                log("事务 {} preWrite阶段发现有Lock冲突;即将进行 {} 时间后的重试", txnId, retryTime);
            }
            Thread.sleep(retryTime);
        }

    }

    public boolean commit() throws Exception {
        List<String> keys = entries.stream()
                .map(e -> e.getKey())
                .collect(Collectors.toList());

        // Step 1: 提交主键
        long commitTS = (commitTs == 0) ? getCurrentTS() : commitTs;
        CommitArgs primaryArgs = new CommitArgs();
        primaryArgs.setStartTs(startTS);
        primaryArgs.setCommitTs(commitTS);
        primaryArgs.setKeys(Collections.singletonList(primary));
        log("事务 {} 开始执行commit;commitTs = {}", txnId, commitTS);
        CommandResponse response = client.kvCommit("key", primaryArgs);

        if (!response.isSuccess()) {
            // 每次提交前都要检查lock是否存在，不存在可能是被其它事务误以为已经crash而清除
            if(response.getTxnAction() == TxnConstants.ROLLBACK) {
                log("事务 {} 的primary lock不存在;commit失败, 开始执行回滚", txnId);
            }
            rollback();
            return false;
        }

        committed = true;

        // Step 2: 异步提交其他 keys，不阻塞主线程
        List<String> secondaryKeys = new ArrayList<>(keys);
        secondaryKeys.remove(primary); // 剔除主键

        dealing = !secondaryKeys.isEmpty();

        for (String key : secondaryKeys) {
            asyncSecondaryCommitPool.submit(() -> asyncCommitSingleKey(key, commitTS));
        }

        return true;
    }

    public void rollback() throws Exception {
//        log.info("事务 {} 开始回滚", txnId);
        List<String> keys = entries.stream().map(e -> {
            return e.getKey();
        }).collect(Collectors.toList());
        BatchRollbackArgs args = new BatchRollbackArgs();
        args.setStartTs(startTS);
        args.setKeys(keys);

        CommandResponse response = client.kvBatchRollback("key", args);
        if(!response.isSuccess()) {
            if(response.getTxnAction() == TxnConstants.ABORT) {
                // 说明事务已经提交了
                committed = true;
            }
        } else {
            rollback = true;
        }
    }

    private long handleKeyErrors(List<KeyError> errors) throws Exception {
        List<LockInfo> locks = new ArrayList<>();
        for (KeyError err : errors) {
            if (err.getWriteConflict() != null) {
//                WriteConflict wc = err.getWriteConflict();
                // 事务开始之后出现别的事务提交了，出现了写写冲突，需要将本事务rollback
                log("事务 {} 开始之后出现别的事务提交;preWrite失败, 开始执行回滚;", txnId);
                rollback();
                return 0;
            } else if (err.getLockInfo() != null) {
                // 考虑到事务被crash的情况，有重试机制
                locks.add(err.getLockInfo());
            }
        }

        return resolveLocks(locks);
    }

    /**
     * 当一个事务遇到 Lock 时，可能有几种情况。
     *
     * 1. Lock 所属的事务还未提交这个 Key，Lock 尚未被清理；
     * 2. Lock 所属的事务遇到了不可恢复的错误，正在回滚中，尚未清理 Key；
     * 3. Lock 所属事务的节点发生了意外错误，例如节点 crash，这个 Lock 所属的节点已经不能够更新它。
     * 在 Percolator 协议下，会通过查询 Lock 所属的 Primary Key 来判断事务的状态，
     * 但是当读取到一个未完成的事务（Primary Key 的 Lock 尚未被清理）时，我们所期望的，
     * 是等待提交中的事物至完成状态，并且清理如 crash 等异常留下的垃圾数据。
     * 此时会借助 ttl 来判断事务是否过期，遇到过期事务时则会主动 Rollback 它。
     * @param locks
     * @throws Exception
     */
    public long resolveLocks(List<LockInfo> locks) throws Exception {
//        log("事务 {} 在preWrite阶段处理locks;locks: {}", txnId, locks);

        long retryTime = 0;
        for (LockInfo lock : locks) {
            TxnStatus status = checkTxnStatus(lock.getLockTs(), lock.getPrimary());
            if(status.getTtl() == 0) {
                // committed or rollback
                ResolveLockArgs resolveLockArgs = new ResolveLockArgs();
                resolveLockArgs.setStartTs(lock.getLockTs());
                resolveLockArgs.setCommitTs(status.getCommitTS());
                if(status.getCommitTS() == 0) {
                    log("事务 {} 执行将lock primary: {} 回滚;", txnId, lock.getPrimary());
                } else {
                    log("事务 {} 执行将lock primary: {} 提交;", txnId, lock.getPrimary());
                }
                CommandResponse response = client.kvResolveLock("key", resolveLockArgs);
                // 这个response处理的是别的事务，可以不care？
                if(response.getErr().contains("keys is empty")) {
                    log("事务 {} 执行将lock primary: {} 提交/回滚时发现lock不存在;", txnId, lock.getPrimary());
                } else if(response.isSuccess()) {
                    List<String> keys = new ArrayList<>();
                    if(response.getValue() != null) {
                        keys = (List<String>) response.getValue();
                    }
                    if(response.getTxnAction() == TxnConstants.ACTION_ROLLBACK) {
                        BatchRollbackArgs batchRollbackArgs = new BatchRollbackArgs();
                        batchRollbackArgs.setKeys(keys);
                        batchRollbackArgs.setStartTs(lock.getLockTs());
                        CommandResponse response1 = client.kvBatchRollback("key", batchRollbackArgs);
                        if(response1.isSuccess()) {
                            log("事务 {} 执行将keys: {} 回滚成功;", txnId, keys);
                        } else {
                            log("事务 {} 执行将keys: {} 回滚失败;", txnId, keys);
                        }
                    } else if(response.getTxnAction() == TxnConstants.ACTION_COMMIT) {
                        CommitArgs commitArgs = new CommitArgs();
                        commitArgs.setStartTs(lock.getLockTs());
                        commitArgs.setCommitTs(status.getCommitTS());
                        commitArgs.setKeys(keys);
                        CommandResponse response1 = client.kvCommit("key", commitArgs);
                        if(response1.isSuccess()) {
                            log("事务 {} 执行将keys: {} 提交成功;", txnId, keys);
                        } else {
                            log("事务 {} 执行将keys: {} 提交失败;", txnId, keys);
                        }
                    }

                }
            } else {
                // TODO retry
                retryTime = Math.max(retryTime, status.getTtl());
                log("事务 {} 处理Lock primary: {} 时发现其ttl不为0; sleep {}ms后重试", txnId, lock.getPrimary(), retryTime);
            }
        }
        return retryTime;
    }

    public TxnStatus checkTxnStatus(long lockTs, String primary) throws Exception {
        CheckTxnStatusArgs args = new CheckTxnStatusArgs();
        args.setLockTs(lockTs);
        args.setPrimaryKey(primary);
        args.setCommitTs(getCurrentTS());
        TxnStatus txnStatus = new TxnStatus();
        CommandResponse response = client.kvCheckTxnStatus("key", args);
//        log.info("22222: {}", response);

        txnStatus.setTxnAction(response.getTxnAction());
        txnStatus.setCommitTS(0);
        if(response.getValue() != null) {
            CheckTxnStatusReply checkTxnStatusReply = (CheckTxnStatusReply) response.getValue();
            if(checkTxnStatusReply.getLockTtl() != 0) {
                txnStatus.setTtl(checkTxnStatusReply.getLockTtl());
            } else {
                txnStatus.setCommitTS(checkTxnStatusReply.getCommitTs());
            }
        }

        log("事务 {} 查询PrimaryLock: {} 对应的事务状态; 事务信息为: {}", txnId, primary, txnStatus);
        return txnStatus;
    }

//    private void cleanup(long startTS, byte[] primary, byte[] key) throws Exception {
//        ResolveLockArgs args = new ResolveLockArgs();
//        args.setStartTs(startTS);
//        args.setCommitTs(0); // 表示 rollback
//        client.kvResolveLock(args);
//    }

    private void asyncCommitSingleKey(String key, long commitTS) {
        int retry = 0;
        boolean success = false;

        while (retry <= MAX_RETRY && !success) {
            try {
                CommitArgs args = new CommitArgs();
                args.setStartTs(startTS);
                args.setCommitTs(commitTS);
                args.setKeys(Collections.singletonList(key));

                CommandResponse res = client.kvCommit("key", args);

                if (res.isSuccess()) {
                    success = true;
                    log.info("Secondary key committed successfully: " + key);
                } else {
                    retry++;
                    log.warn("Failed to commit secondary key: " + key +
                            ", retrying (" + retry + "/" + MAX_RETRY + ")...");

                    if (retry < MAX_RETRY) {
                        Thread.sleep(retry * RETRY_INTERVAL_MS); // 指数退避
                    }
                }

            } catch (Exception e) {
                retry++;
                log.error("Exception committing secondary key: " + key +
                        ", retrying (" + retry + "/" + MAX_RETRY + ")");
                e.printStackTrace();

                if (retry < MAX_RETRY) {
                    try {
                        Thread.sleep(retry * RETRY_INTERVAL_MS);
                    } catch (InterruptedException ignored) {}
                }
            }
        }

        if (!success) {
            // 超出最大重试次数仍未成功，不成功会影响Get
            // TODO:
            log("事务 {} 执行时超过最大重试次数;", txnId);
        }

        // 最终判断是否还有任务在进行
        updateDealingStatus();
    }

    public String get(String key) throws Exception {
        GetArgs args = new GetArgs();
        args.setTs(startTS);

        int retry = 0;
        boolean success = false;
        String resValue = "get error!";
        while (retry <= MAX_RETRY && !success) {
            CommandResponse res = client.kvGet(key, args);
            if (!res.isSuccess()) {
                if (res.getTxnAction() == TxnConstants.READ_LOCKCONFLICT && !res.getKeyErrors().isEmpty()) {
                    List<KeyError> keyErrors = res.getKeyErrors();
                    List<LockInfo> locks = new ArrayList<>();
                    for (KeyError err : keyErrors) {
                        if(err.getLockInfo() != null) {
                            locks.add(err.getLockInfo());
                        }
                    }
                    long retryTime = resolveLocks(locks);
                    Thread.sleep(retryTime);
                    // 再试一次
                } else {
                    Thread.sleep(500);
                }
            } else {
                success = true;
                resValue = (String)res.getValue();
            }
        }


        return resValue;
    }

    private long getCurrentTS() {
        return timestampOracle.getTimestamp();
    }

    public boolean isRollback() {
        return rollback;
    }

    public boolean isCommitted() {
        return committed;
    }

    public boolean isDealing() {
        if(!dealing) return false;
        int activeTasks = asyncSecondaryCommitPool.getActiveCount();
        int queuedTasks = asyncSecondaryCommitPool.getQueue().size();
        dealing = (activeTasks + queuedTasks) > 0;
        return dealing;
    }

    private synchronized void updateDealingStatus() {
        int activeTasks = asyncSecondaryCommitPool.getActiveCount();
        int queuedTasks = asyncSecondaryCommitPool.getQueue().size();
        dealing = (activeTasks + queuedTasks) > 0;
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

}
