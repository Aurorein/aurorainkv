package com.aurorain.shardkv.web;

import com.aurorain.commonmodule.model.ResultData;
import com.aurorain.raft.model.Entry;
import com.aurorain.shardkv.Client;
import com.aurorain.shardkv.KVRaftConfig;
import com.aurorain.shardkv.common.CommandType;
import com.aurorain.shardkv.model.Command;
import com.aurorain.shardkv.web.dto.*;
import com.aurorain.shardkv.web.util.ChannelUtils;
import com.aurorain.shardkv.web.util.ShardKvConfigUtil;
import com.aurorain.shardkv.web.util.ShardKvWebUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/kvclient")
public class KVClientController {

    @Autowired
    ShardKvWebUtil shardKvWebUtil;

    @Autowired
    ShardKvConfigUtil shardKvConfigUtil;

    @PostMapping("/get")
    public ResultData<String> get(@RequestBody GetReq getReq) {
        Client client = shardKvWebUtil.getClient();
        String value = client.get(getReq.getKey());
        return ResultData.success(value);
    }

    @PostMapping("/put")
    public ResultData<Object> put(@RequestBody PutReq putReq) {
        Client client = shardKvWebUtil.getClient();
        client.put(putReq.getKey(), putReq.getValue());
        return ResultData.success(null);
    }

    @PostMapping("/batch")
    public ResultData<List<String>> batchOperations(@RequestBody List<BatchOperation> operations) {
        Client client = shardKvWebUtil.getClient();
        List<String> results = new ArrayList<>();

        for (BatchOperation op : operations) {
            switch (op.getType()) {
                case "GET":
                    results.add(client.get(op.getKey()));
                    break;
                case "PUT":
                    client.put(op.getKey(), op.getValue());
                    results.add("PUT_SUCCESS");
                    break;
                case "APPEND":
                    client.append(op.getKey(), op.getValue());
                    results.add("APPEND_SUCCESS");
                    break;
            }
        }

        return ResultData.success(results);
    }

    @GetMapping("/consistency-test")
    public ResultData<ConsistencyTestResult> runConsistencyTest() {
        // 1. 初始化参数
        final int CLIENTS = 3;
        final int SERVERS = 3;
        final String KEY_PREFIX = "ct_";
        final String[] KEYS = {KEY_PREFIX + "a", KEY_PREFIX + "b", KEY_PREFIX + "c"};
        final int TEST_ROUNDS = 3;
        final int OPERATION_DURATION_MS = 3000;

        // 2. 结果收集
        List<RoundResult> roundResults = new ArrayList<>();

        for (int round = 0; round < TEST_ROUNDS; round++) {
            // 3. 启动客户端线程
            AtomicBoolean running = new AtomicBoolean(true);
            List<ClientResult> clientResults = Collections.synchronizedList(new ArrayList<>());
            List<Thread> clientThreads = new ArrayList<>();

            Map<Integer, Map<Integer, String>> retrievedMap = new ConcurrentHashMap<>();

            for (int clientId = 0; clientId < CLIENTS; clientId++) {
                final int finalClientId = clientId;
                Thread t = new Thread(() -> {
                    Client client = shardKvWebUtil.newClient();
                    try {
//                         初始化所有key
                        for (String key : KEYS) {
                            client.put(key, String.valueOf(ThreadLocalRandom.current().nextInt(1, 11)));
                        }

                        // 初始化client的retrievedMap
                        retrievedMap.put(client.getClerk().getClientId(), new ConcurrentHashMap<>());

                        // 执行随机操作
                        while (running.get()) {
                            String key = KEYS[ThreadLocalRandom.current().nextInt(KEYS.length)];
                            String value = String.valueOf(ThreadLocalRandom.current().nextInt(1, 11));
                            if (ThreadLocalRandom.current().nextBoolean()) {
                                // PUT操作
                                client.put(key, value);
                                log.info("put op key:{} value:{}", key, value);
                            } else {
                                // GET操作
                                String retrieved = client.get(key);
                                retrievedMap.get(client.getClerk().getClientId()).put(client.getClerk().getSeqId(), retrieved);
                                log.info("get op ke y:{} retrieved:{}", key, retrieved);
                                // GET结果会通过Raft日志验证，此处仅记录错误
                            }
                            Thread.sleep(100);
                        }
                    } catch (Exception e) {
                        clientResults.add(new ClientResult(finalClientId, "ERROR", e.getMessage()));
                    }
                });
                t.start();
                clientThreads.add(t);
            }

            // 4. 等待操作执行
            try {
                Thread.sleep(OPERATION_DURATION_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 5. 停止客户端
            running.set(false);
            for (Thread t : clientThreads) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            // 6. 获取Raft日志并分析
            // 测试的时候固定是gid 1
            KVRaftConfig config = shardKvConfigUtil.getConfigs().get(1);
            int leaderId = 1;
            for(int id : config.getIds()) {
                if(config.getShardKvServers().get(id).getState().getRaft().isLeader()) {
                    leaderId = id;
                }
            }
            // 根据Leader得到所有的日志
            Entry[] raftLogs = config.getShardKvServers().get(leaderId).getState().getRaft().getRaftState().getLogs();

            // 7. 提取并验证操作
            List<VerifiedOperation> verifiedOps = analyzeRaftLogs(raftLogs, KEY_PREFIX, retrievedMap);

            // 8. 记录本轮结果
            roundResults.add(new RoundResult(
                    round,
                    verifiedOps,
                    clientResults,
                    verifiedOps.stream().noneMatch(op -> op.getType() == CommandType.GET && !op.isValid())
            ));
        }

        // 9. 返回最终结果
        return ResultData.success(new ConsistencyTestResult(
                CLIENTS,
                SERVERS,
                roundResults,
                roundResults.stream().allMatch(RoundResult::isValid)
        ));
    }

    private List<VerifiedOperation> analyzeRaftLogs(Entry[] raftLogs, String keyPrefix, Map<Integer, Map<Integer, String>> retrievedMap) {
        List<Operation> operations = new ArrayList<>();
        // 1. 提取所有相关操作
        for (Entry entry : raftLogs) {
            if (entry.getCommand() instanceof Command) {
                Command<String> cmd = (Command<String>) entry.getCommand();
                if (cmd.getKey().startsWith(keyPrefix) && retrievedMap.containsKey(cmd.getClientId())) {
                    operations.add(new Operation(
                            entry.getIndex(),
                            cmd.getType(),
                            cmd.getKey(),
                            cmd.getValue(),
                            entry.getTerm(),
                            cmd.getClientId(),
                            cmd.getSeqId()
                    ));
                }
            }
        }

        // 2. 按日志索引排序
        operations.sort(Comparator.comparingInt(Operation::getLogIndex));

        // 3. 验证操作序列
        List<VerifiedOperation> verifiedOps = new ArrayList<>();
        Map<String, String> lastPutValues = new HashMap<>();
        int retrievedIdx = 0;

        log.info("retrievedMap : {}", retrievedMap);
        log.info("operations : {}", operations);
        for (Operation op : operations) {
            switch (op.getType()) {
                case PUT:
                    lastPutValues.put(op.getKey(), op.getValue());
                    verifiedOps.add(new VerifiedOperation(
                            op.getLogIndex(),
                            op.getType(),
                            op.getKey(),
                            op.getValue(),
                            true,
                            op.getValue(),
                            op.getTerm(),
                            op.getClientId(),
                            op.getSeqId()
                    ));
                    break;

                case GET:
                    String expected = lastPutValues.getOrDefault(op.getKey(), "");
                    String retrievedValue = retrievedMap.get(op.getClientId()).get(op.getSeqId());
                    retrievedIdx++;
                    boolean isValid = retrievedValue.equals(expected);
                    verifiedOps.add(new VerifiedOperation(
                            op.getLogIndex(),
                            op.getType(),
                            op.getKey(),
                            retrievedValue,
                            isValid,
                            expected,
                            op.getTerm(),
                            op.getClientId(),
                            op.getSeqId()
                    ));
                    break;
            }
        }

        return verifiedOps;
    }

}
