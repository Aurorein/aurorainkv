package com.aurorain.shardkv;

import com.aurorain.shardkv.model.dto.*;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardmaster.ShardConfig;
import com.aurorain.shardkv.common.CommandType;
import com.aurorain.shardkv.model.Clerk;
import com.aurorain.shardkv.model.Command;
import com.aurorain.shardkv.service.KVServerService;
import com.aurorain.commonmodule.utils.RpcUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.aurorain.shardmaster.constant.ShardConstant.NShard;

/**
 * @author aurorain
 * @version 1.0
 */
@Slf4j
public class Client {

    private boolean isKilled;

    @Getter
    private final Clerk clerk = new Clerk();

    // gid -> {id -> service}
    private Map<Integer, Map<Integer, KVServerService>> services;

    private ShardClient shardClient;

    public Map<Integer, KVServerService> getKvServices(int gid) {
        return services.get(gid);
    }

    private ShardConfig cfg;

    // gid -> leaderId
    private Map<Integer, Integer> leaderIdMap;

    @JsonIgnore
    private transient ReentrantLock lock;  // 互斥锁

    public Client(ShardClient shardClient) {
        clerk.setClientId((int) ((Math.random() * 9 + 1) * 100000));
        this.services = new HashMap<>();
        this.shardClient = shardClient;
        this.leaderIdMap = new HashMap<>();
        isKilled = false;
        this.cfg = shardClient.query(-1);
        this.lock = new ReentrantLock();
    }

    public void addGroup(int gid, Map<Integer, KVServerService> kvServices) {
        services.put(gid, kvServices);
        leaderIdMap.put(gid, 0);
    }

    public Map<Integer, Map<Integer, KVServerService>> getServices() {
        return services;
    }

    /**
     * 发送一条命令
     *
     * @return 若为 GET 则返回 value
     */
//    public String sendCommand(String key, String value, CommandType type) {
//        Command command = getCommand(key, value, type);
//        CommandRequest request = new CommandRequest();
//        request.setCommand(command);
//
        // 10 秒内等待来自 server 的 response
//     LocalDateTime time = LocalDateTime.now().plusSeconds(10);
//    while (LocalDateTime.now().isBefore(time)) {
//        CommandResponse<String> response = RpcUtils.call(clerk.getClientId(), clerk.getLeaderId(), clerk.getServices()[clerk.getLeaderId()], "requestCommand", request);
//        if (response != null && response.isSuccess()) {
//            return response.getValue();
//        }
//        // 如果失败则遍历 server 找出 leader
//        clerk.setLeaderId((clerk.getLeaderId() + 1) % clerk.getServices().length);
//        try {
//            Thread.sleep(TimeConstant.RETRY_TIMEOUT);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    return "";
//
//    }
    private void updateConfigLoop() {
        while(!isKilled) {
            ShardConfig config = shardClient.query(-1);
            cfg = config;
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public<T> String sendCommand(String key, T value, CommandType type, String methodName) {

        while(true) {
            int shard = key2shard(key);
            int gid = cfg.getShards()[shard];
            if(cfg.getGroups().containsKey(gid)) {
                List<Integer> sids = cfg.getGroups().get(gid);
                int leaderId = leaderIdMap.get(gid);
                for(int i = 0; i < sids.size(); ++i) {
                    int curLeaderId = (i + leaderId) % sids.size();
                    int sid = sids.get(curLeaderId);
                    CommandResponse res = rpcCall(key, value, type, services.get(gid).get(sid), methodName);
                    log.info("res: {}", res);
                    if(res.isSuccess()) {
                        leaderIdMap.put(gid, curLeaderId);
                        return (String)res.getValue();
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            log.info("shardKvClient sendCommand: type {}, key {}", type, key);
            ShardConfig config = shardClient.query(-1);
            cfg = config;
            log.info("config: {}", config.toJsonString());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public<T> CommandResponse sendCommandV2(String key, T value, CommandType type, String methodName) {
        lock.lock();
        while(true) {
            int shard = key2shard(key);
            int gid = cfg.getShards()[shard];
            if(cfg.getGroups().containsKey(gid)) {
                List<Integer> sids = cfg.getGroups().get(gid);
                int leaderId = leaderIdMap.get(gid);
                for(int i = 0; i < sids.size(); ++i) {
                    int curLeaderId = (i + leaderId) % sids.size();
                    int sid = sids.get(curLeaderId);
                    log.info("send   key: {}, value: {}", key, value);
                    CommandResponse res = rpcCall(key, value, type, services.get(gid).get(sid), methodName);
                    log.info("key: {}, value: {}, res: {}", key, value, res);
                    if(res.isSuccess()) {
                        leaderIdMap.put(gid, curLeaderId);
                        lock.unlock();
                        return res;
                    } else {
                        if(res.getErr() != null && (res.getErr().contains("not leader!") || res.getErr().contains("iterator is null"))) {
                        } else {
                            lock.unlock();
                            return res;
                        }
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        lock.unlock();
                        throw new RuntimeException(e);
                    }
                }
            }
            log.info("shardKvClient sendCommand: type {}, key {}", type, key);
            ShardConfig config = shardClient.query(-1);
            cfg = config;
            log.info("config: {}", config.toJsonString());
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                lock.unlock();
                throw new RuntimeException(e);
            }
        }
    }

    public<T> Command getCommand(String key, T value, CommandType type) {
        clerk.setSeqId(clerk.getSeqId() + 1);
        Command command = new Command();
        command.setClientId(clerk.getClientId());
        command.setSeqId(clerk.getSeqId());
        command.setKey(key);
        command.setValue(value);
        command.setType(type);
        return command;
    }

    public<T> CommandResponse<T> rpcCall(String key, T value, CommandType type ,KVServerService kvServerService, String methodName) {
        Command command = getCommand(key, value, type);
        CommandRequest request = new CommandRequest();
        request.setCommand(command);

        CommandResponse<T> response = RpcUtils.call(clerk.getClientId(), -1, kvServerService, methodName, request);
        // 10 秒内等待来自 server 的 response
//        LocalDateTime time = LocalDateTime.now().plusSeconds(10);
//        while (LocalDateTime.now().isBefore(time)) {
//            CommandResponse<T> response = RpcUtils.call(clerk.getClientId(), to, clerk.getServices()[to], methodName, request);
//            if (response != null && response.isSuccess()) {
//                return response.getValue();
//            }
//            // 如果失败则遍历 server 找出 leader
//            clerk.setLeaderId((clerk.getLeaderId() + 1) % clerk.getServices().length);
//            try {
//                Thread.sleep(TimeConstant.RETRY_TIMEOUT);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
//        }
//
//        return null;
        return response;
    }

    public String get(String key) {
        return sendCommand(key, "", CommandType.GET, "requestCommand");
    }

    public void put(String key, String value) {
        sendCommand(key, value, CommandType.PUT, "requestCommand");
    }

    public void append(String key, String value) {
        sendCommand(key, value, CommandType.APPEND, "requestCommand");
    }

    public CommandResponse kvGet(String key, GetArgs args) {
        return sendCommandV2(key, args, null, "kvGet");
    }

    public CommandResponse kvPreWrite(String key, PreWriteArgs args) {
        return sendCommandV2(key, args, null, "kvPrewrite");
    }

    public CommandResponse kvCommit(String key, CommitArgs args) {
        return sendCommandV2(key, args, null, "kvCommit");
    }

    public CommandResponse kvBatchRollback(String key, BatchRollbackArgs args) {return sendCommandV2(key, args, null, "KvBatchRollback");}

    public CommandResponse kvCheckTxnStatus(String key, CheckTxnStatusArgs args) {return sendCommandV2(key, args, null, "kvCheckTxnStatus");}

    public CommandResponse kvResolveLock(String key, ResolveLockArgs args) {return sendCommandV2(key, args, null, "kvResolveLock");}

    public int getId() {
        return clerk.getClientId();
    }

    public int key2shard(String key) {
        int shard = 0;
        if(key.length() > 0) {
            shard = key.charAt(0) - 'a';
        }
        shard = shard % NShard;
        return shard;
    }

    public void close() {
        this.isKilled = true;
    }



}
