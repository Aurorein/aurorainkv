package com.aurorain;

import com.aurorain.common.CommandType;
import com.aurorain.model.Clerk;
import com.aurorain.model.Command;
import com.aurorain.model.dto.CommandRequest;
import com.aurorain.model.dto.CommandResponse;
import com.aurorain.service.KVServerService;
import com.aurorain.utils.RpcUtils;
import lombok.extern.slf4j.Slf4j;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aurorain.constant.ShardConstant.NShard;

/**
 * @author aurorain
 * @version 1.0
 */
@Slf4j
public class Client {

    private boolean isKilled;

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

    public Client(ShardClient shardClient) {
        clerk.setClientId((int) ((Math.random() * 9 + 1) * 100000));
        this.services = new HashMap<>();
        this.shardClient = shardClient;
        this.leaderIdMap = new HashMap<>();
        isKilled = false;
        this.cfg = shardClient.query(-1);
    }

    public void addGroup(int gid, Map<Integer, KVServerService> kvServices) {
        services.put(gid, kvServices);
        leaderIdMap.put(gid, 0);
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

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public String sendCommand(String key, String value, CommandType type) {

        while(true) {
            int shard = key2shard(key);
            int gid = cfg.getShards()[shard];
            if(cfg.getGroups().containsKey(gid)) {
                List<Integer> sids = cfg.getGroups().get(gid);
                int leaderId = leaderIdMap.get(gid);
                for(int i = 0; i < sids.size(); ++i) {
                    int curLeaderId = (i + leaderId) % sids.size();
                    int sid = sids.get(curLeaderId);
                    CommandResponse<String> res = rpcCall(key, value, type, services.get(gid).get(sid), "requestCommand");
                    if(res.isSuccess()) {
                        leaderIdMap.put(gid, curLeaderId);
                        return res.getValue();
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

    public Command getCommand(String key, String value, CommandType type) {
        clerk.setSeqId(clerk.getSeqId() + 1);
        Command command = new Command();
        command.setClientId(clerk.getClientId());
        command.setSeqId(clerk.getSeqId());
        command.setKey(key);
        command.setValue(value);
        command.setType(type);
        return command;
    }

    public<T> CommandResponse<T> rpcCall(String key, String value, CommandType type ,KVServerService kvServerService, String methodName) {
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
        return sendCommand(key, "", CommandType.GET);
    }

    public void put(String key, String value) {
        sendCommand(key, value, CommandType.PUT);
    }

    public void append(String key, String value) {
        sendCommand(key, value, CommandType.APPEND);
    }

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
