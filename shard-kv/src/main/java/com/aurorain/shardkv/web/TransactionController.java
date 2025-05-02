package com.aurorain.shardkv.web;

import java.sql.Time;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.aurorain.commonmodule.model.ResultData;
import com.aurorain.shardkv.Client;
import com.aurorain.shardkv.constant.CFConstants;
import com.aurorain.shardkv.model.TransactionArgs;
import com.aurorain.shardkv.model.TransactionReply;
import com.aurorain.shardkv.model.dto.TransactionOper;
import com.aurorain.shardkv.store.KV;
import com.aurorain.shardkv.store.RocksDBEntry;
import com.aurorain.shardkv.store.RocksDBKV;
import com.aurorain.shardkv.transaction.TransactionExecutorManager;
import com.aurorain.shardkv.tso.TimestampOracle;
import com.aurorain.shardkv.web.util.ShardKvConfigUtil;
import com.aurorain.shardkv.web.util.ShardKvWebUtil;
import com.aurorain.shardkv.web.util.TimestampOracleUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@CrossOrigin("*")
@RequestMapping("/transaction")
public class TransactionController {

    @Autowired
    ShardKvWebUtil shardKvWebUtil;

    @Autowired
    ShardKvConfigUtil shardKvConfigUtil;

    @Autowired
    TimestampOracleUtil timestampOracleUtil;

    @PostMapping("/transactionTest")
    public ResultData<TransactionReply> transactionTest(@RequestBody TransactionArgs transactionArgs) {
        int txnSize = transactionArgs.getTxns().size();
        // 按照txn排序顺序执行
        List<TransactionOper> txns = transactionArgs.getTxns().stream().sorted((o1, o2) -> (int) (o1.getTxnId() - o2.getTxnId())).collect(Collectors.toList());

        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(3);
        TimestampOracle timestampOracle = timestampOracleUtil.getTimestampOracle();
        List<String> logs = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch countDownLatch = new CountDownLatch(txnSize);
        for(int i = 0; i < txnSize; ++i) {
            TransactionOper txnOp = txns.get(i);
            Client client = shardKvWebUtil.newClient();
            TransactionExecutorManager transactionExecutorManager = new TransactionExecutorManager(client, timestampOracle, txnOp.getTxnId(), logs, countDownLatch);

            switch(txnOp.getOp()) {
                case 1:{
                    newFixedThreadPool.submit(() -> transactionExecutorManager.execute2PC(txnOp.getPrimary(), txnOp.getEntries()));
                    break;
                }
                case 2:{
                    newFixedThreadPool.submit(() -> transactionExecutorManager.crash(txnOp.getPrimary(), txnOp.getEntries()));
                    break;
                }
                case 3:{
                    newFixedThreadPool.submit(() -> transactionExecutorManager.get(txnOp.getEntries().get(0).getKey()));
                    break;
                }
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        TransactionReply transactionReply = new TransactionReply();
        transactionReply.setLogs(logs);

        // TODO Test
        RocksDBKV store = (RocksDBKV)shardKvConfigUtil.getConfigs().get(1).getShardKvServers().get(1).getState().getStore();
        store.iterateColumnFamily(CFConstants.CfDefault);
        store.iterateColumnFamily(CFConstants.CfLock);
        store.iterateColumnFamily(CFConstants.CfWrite);

        return ResultData.success(transactionReply);

    }
}
