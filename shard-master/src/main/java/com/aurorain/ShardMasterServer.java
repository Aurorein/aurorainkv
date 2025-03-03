package com.aurorain;

import com.aurorain.common.ShardCommandContext;
import com.aurorain.common.ShardCommandType;
import com.aurorain.constant.Message;
import com.aurorain.constant.ShardConstant;
import com.aurorain.constant.TimeConstant;
import com.aurorain.model.*;
import com.aurorain.model.dto.dto.ShardCommandRequest;
import com.aurorain.model.dto.dto.ShardCommandResponse;
import com.aurorain.service.ShardServerService;
import com.aurorain.model.Channel;
import com.aurorain.model.dto.ApplyMsg;
import com.aurorain.server.VertxHttpServer;
import com.aurorain.service.RaftService;
import lombok.extern.slf4j.Slf4j;


import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.aurorain.constant.ShardConstant.NShard;

@Slf4j
public class ShardMasterServer implements ShardServerService {

    private ShardServerState state = new ShardServerState();

    public VertxHttpServer init(int me, Map<Integer, RaftService> peers, Persister persister, int maxRaftSate) {
        state.setMe(me);
        state.setMaxRaftState(maxRaftSate);
        state.setChannel(new Channel<>(5));
        state.setConfigs(new ArrayList<>());
        ShardConfig shardConfig = new ShardConfig();
        HashMap<Integer, List<Integer>> groups = new HashMap<>();
//        ArrayList<Integer> list = new ArrayList<>();
//        for(int i = 0; i < NShard; ++i) {
//            list.add(i);
//        }
//        groups.put(1, list);
        shardConfig.setGroups(groups);
        state.getConfigs().add(shardConfig);
        int[] shards = new int[NShard];
        shardConfig.setShards(shards);

        Raft raft = new Raft();
        state.setRaft(raft);
        raft.init(peers, me, persister, state.getChannel());
        state.setCmdResponseChannels(new HashMap<>());
        state.setLastCmdContext(new HashMap<>());
        state.setLastApplied(0);

        new Thread(this::applier).start();
        return new VertxHttpServer(raft);
    }

    @Override
    public ShardCommandResponse requestCommand(ShardCommandRequest request) {

        // lock
        state.getLock().lock();
        Args args = request.getArgs();
        ShardCommandType shardCommandType = args.getType();
        ShardCommandType type = args.getType();
        int clientId = args.getClientId();
        int seqId = args.getSeqId();

        // TODO 校验操作是否可行
//        log.info("request command: type {}, seqId {}", shardCommandType, seqId);
        ShardCommandResponse response = new ShardCommandResponse();
//        Args cmd = new Args();
//        cmd.setType(type);
//        cmd.setSeqId(args.getSeqId());

        if(type.equals(ShardCommandType.QUERY)) {
//            log.info("query execute...");
            QueryReply queryReply = query((QueryArgs) args);
            response.setSuccess(true);
            response.setValue(queryReply.getConfig());
            return response;
        }

        if (isDuplicated(clientId, seqId)) {
            log.info("duplicated clientId and seqId");
            // 如果存在对应的命令记录则获取 response
            ShardCommandContext context = state.getLastCmdContext().get(clientId);
            state.getLock().unlock();
            return context.getResponse();
        }
        state.getLock().unlock();
        // 将命令追加到 raft 之中，随后开启通道等待来自 applier 的消息
        Raft raft = state.getRaft();
        int index = raft.startCmd(args);
        int term = raft.getTerm();
        // 检查状态，是否为 leader
        if (index == -1) {
            log.info("shard master: wrong leader, leader id :{}!", state.getMe());
            response.setValue("");
            response.setSuccess(false);
            response.setErr(Message.WRONG_LEADER);
            return response;
        }

        log.error("shard master: correct leader, leader id :{}!", state.getMe());
        state.getLock().lock();
        // 新建一个通道，供 applier 写入，该函数只创建通道和读取通道的信息
        ShardIndexAndTerm it = new ShardIndexAndTerm(index, term);
        log.info("shardMatser indexAndTerm :{} and {}", index, term);
        Channel<ShardCommandResponse> channel = new Channel<>(1);
        state.getCmdResponseChannels().put(it, channel);
        state.getLock().unlock();

        // 在一定的时间段里反复读取通道里的消息
        LocalDateTime time = LocalDateTime.now().plus(TimeConstant.CMD_TIMEOUT, ChronoUnit.MILLIS);
        while (LocalDateTime.now().isBefore(time)) {
            state.getLock().lock();
            ShardCommandResponse res = null;
            if ((res = channel.read()) != null) {
                // 销毁通道
                state.getCmdResponseChannels().remove(it);
                state.getLock().unlock();
                return res;
            }
            state.getLock().unlock();
            try {
                Thread.sleep(TimeConstant.GAP_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        response.setValue("");
        response.setSuccess(false);
        response.setErr(Message.TIMEOUT);
        state.getCmdResponseChannels().remove(it);
        return response;
    }

    public void applier() {
        Channel<ApplyMsg> channel = state.getChannel();
        while (!killed()) {
            ApplyMsg applyMsg = null;
            if ((applyMsg = channel.read()) != null) {
                if (applyMsg.isCommandValid()) {
                    state.getLock().lock();
                    // 检查消息是否过时
                    if (applyMsg.getCommandIndex() < state.getLastApplied()) {
                        state.getLock().unlock();
                        continue;
                    }
                    state.setLastApplied(applyMsg.getCommandIndex());

                    // 判断命令类型并记录
                    Args args = (Args) applyMsg.getCommand();
                    ShardCommandResponse response = new ShardCommandResponse();
                    int seqId = args.getSeqId();
                    int clientId = args.getClientId();

                    log.info("shard master: received applyMsg: type {}, seqId {}, clientId {}", args.getType(), args.getSeqId(), args.getClientId());
                    if (args.getType() != ShardCommandType.QUERY && isDuplicated(clientId, seqId)) {
                        // 如果存在对应的命令记录则获取 response
                        ShardCommandContext context = state.getLastCmdContext().get(clientId);
                        response = context.getResponse();
                    }

                    switch(args.getType()) {
                        case JOIN: {
                            log.info("进入join逻辑~");
                            join((JoinArgs) args);

                            response.setValue(null);
                            response.setSuccess(true);
                            // 更新命令上下文
                            ShardCommandContext context = new ShardCommandContext(seqId, response);
                            state.getLastCmdContext().put(clientId, context);
                            break;
                        }
                        case LEAVE: {
                            leave((LeaveArgs) args);

                            response.setValue(null);
                            response.setSuccess(true);
                            // 更新命令上下文
                            ShardCommandContext context = new ShardCommandContext(seqId, response);
                            state.getLastCmdContext().put(clientId, context);
                            break;
                        }
                        case MOVE: {
                            move((MoveArgs) args);

                            response.setValue(null);
                            response.setSuccess(true);
                            // 更新命令上下文
                            ShardCommandContext context = new ShardCommandContext(seqId, response);
                            state.getLastCmdContext().put(clientId, context);
                            break;
                        }
//                        case QUERY: {
//                            QueryReply query = query((QueryArgs) args);
//
//                            response.setValue(query);
//                            response.setSuccess(true);
//                            // 更新命令上下文
//                            CommandContext context = new CommandContext(seqId, response);
//                            state.getLastCmdContext().put(clientId, context);
//                            break;
//                        }
                        default:{
                            //
                            throw new RuntimeException("未知类型!");
                        }
                    }
                    log.info("return response: {}", response.toString());
                    // 检查状态
                    if (!state.getRaft().isLeader() || applyMsg.getCommandTerm() != state.getRaft().getTerm()) {
                        state.getLock().unlock();
                        continue;
                    }
                    log.info("responseChanndel write......");
                    // 通过通道把 response 返回给 handler
                    Channel<ShardCommandResponse> responseChannel = state.getCmdResponseChannels().get(new ShardIndexAndTerm(applyMsg.getCommandIndex(), applyMsg.getCommandTerm()));
                    log.info("indexAndTerm :{} and {}", applyMsg.getCommandIndex(), applyMsg.getCommandTerm());
                    if (responseChannel != null && (!responseChannel.write(response))) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    state.getLock().unlock();
                }
            } else {
                try {
                    Thread.sleep(TimeConstant.GAP_TIME);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void join(JoinArgs joinArgs) {
        ShardConfig config = state.getConfigByIndex(-1);
        config.setNum(config.getNum() + 1);

        joinArgs.getServers().forEach((k, v) -> {
            config.getGroups().put(k, v);
        });

        adjustConfig(config);
        state.getConfigs().add(config);
        log.info("finished join......");
    }

    private void leave(LeaveArgs leaveArgs) {
        ShardConfig config = state.getConfigByIndex(-1);
        config.setNum(config.getNum() + 1);

        for(int gid : leaveArgs.getGids()) {
            config.getGroups().remove(gid);
            for(int i = 0; i < config.getShards().length; ++i) {
                if(config.getShards()[i] == gid) {
                    config.getShards()[i] = 0;
                }
            }
        }
        adjustConfig(config);
        state.getConfigs().add(config);
    }

    private void move(MoveArgs moveArgs) {
        ShardConfig config = state.getConfigByIndex(-1);
        config.setNum(config.getNum() + 1);

        config.getShards()[moveArgs.getShard()] = moveArgs.getGid();
//        adjustConfig(config);
        state.getConfigs().add(config);
    }

    private QueryReply query(QueryArgs queryArgs) {
        QueryReply queryReply = new QueryReply();
        if(queryArgs.getNum() < 0 || queryArgs.getNum() >= state.getConfigs().size()) {
            queryReply.setConfig(state.getConfigByIndex(state.getConfigs().size() - 1).clone());
        } else {
            queryReply.setConfig(state.getConfigByIndex(queryArgs.getNum()).clone());
        }
        return queryReply;
    }

    public void adjustConfig(ShardConfig config) {
        log.info("execute adjust config......");
        if(config.getGroups().size() == 0) {
            config.setShards(new int[NShard]);
            return;
        }
        if(config.getGroups().size() == 1) {
            // 获取 groups 中唯一的 key
            int uniqueKey = config.getGroups().keySet().iterator().next();

            int[] initArr = new int[NShard];
            Arrays.fill(initArr, uniqueKey);
            config.setShards(initArr);
            return;
        }
        if(config.getGroups().size() < NShard) {
            // 计算每个 Group 的平均分片数
            int numGroups = config.getGroups().size();
            int avgShardsPerGroup = NShard / numGroups;
            int extraShards = NShard % numGroups;

            // 统计每个 Group 的当前分片数
            Map<Integer, Integer> shardCount = new HashMap<>();
            for (int gid : config.getShards()) {
                shardCount.put(gid, shardCount.getOrDefault(gid, 0) + 1);
            }

            // 先处理 current > target 的情况
            for (int gid : config.getGroups().keySet()) {
                int target = avgShardsPerGroup;
                if (extraShards > 0) {
                    target++;
                    extraShards--;
                }

                int current = shardCount.getOrDefault(gid, 0);
                if (current > target) {
                    // 减少分片数
                    int count = 0;
                    for (int i = 0; i < config.getShards().length; i++) {
                        if (config.getShards()[i] == gid) {
                            if (current - count > target) {
                                config.getShards()[i] = 0; // 标记为未分配
                                count++;
                            }
                        }
                    }
                }
            }

            // 再处理 current < target 的情况
            for (int gid : config.getGroups().keySet()) {
                int target = avgShardsPerGroup;
                if (extraShards > 0) {
                    target++;
                    extraShards--;
                }

                int current = shardCount.getOrDefault(gid, 0);
                if (current < target) {
                    // 增加分片数
                    int count = 0;
                    for (int i = 0; i < config.getShards().length; i++) {
                        if (config.getShards()[i] == 0 && count < (target - current)) {
                            config.getShards()[i] = gid;
                            count++;
                        }
                    }
                }
            }

            // 将未分配的分片分配给最后一个 Group
            int lastGid = 0;
            for (int gid : config.getGroups().keySet()) {
                lastGid = gid;
            }
            for (int i = 0; i < config.getShards().length; i++) {
                if (config.getShards()[i] == 0) {
                    config.getShards()[i] = lastGid;
                }
            }
        } else {
            // Groups 数量大于 NShards，每个 Group 最多分配一个分片
            Map<Integer, Boolean> assignedGids = new HashMap<>();
            List<Integer> emptyShards = new ArrayList<>();

            // 标记已分配的 Group 和未分配的分片
            for (int i = 0; i < config.getShards().length; i++) {
                int gid = config.getShards()[i];
                if (gid == 0) {
                    emptyShards.add(i);
                } else if (assignedGids.containsKey(gid) && assignedGids.get(gid)) {
                    emptyShards.add(i);
                    config.getShards()[i] = 0;
                } else {
                    assignedGids.put(gid, true);
                }
            }

            // 将未分配的分片分配给未分配的 Group
            List<Integer> gids = new ArrayList<>();
            for (int gid : config.getGroups().keySet()) {
                if (!assignedGids.containsKey(gid) || !assignedGids.get(gid)) {
                    gids.add(gid);
                }
            }
            Collections.sort(gids);

            for (int i = 0; i < emptyShards.size(); i++) {
                if (i >= gids.size()) {
                    break;
                }
                config.getShards()[emptyShards.get(i)] = gids.get(i);
            }
        }


    }

    private boolean killed() {
        return state.getDead().get() == 1;
    }

    private boolean isDuplicated(int clientId, int seqId) {
        ShardCommandContext context = state.getLastCmdContext().get(clientId);
        return context != null && context.getSeqId() >= seqId;
    }

    public void kill() {
        state.getDead().set(1);
        state.getRaft().kill();
    }

    public boolean isLeader() {
        return state.getRaft().isLeader();
    }

}
