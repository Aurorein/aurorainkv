package com.aurorain;

import com.aurorain.common.ShardCommandType;
import com.aurorain.constant.TimeConstant;
import com.aurorain.model.*;
import com.aurorain.model.dto.dto.ShardCommandRequest;
import com.aurorain.model.dto.dto.ShardCommandResponse;
import com.aurorain.service.ShardServerService;
import com.aurorain.utils.RpcUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
public class ShardClient {

    private final ShardClerk shardClerk = new ShardClerk();

    ShardClient(ShardServerService[] services) {
        shardClerk.setServices(services);
        shardClerk.setClientId((int) ((Math.random() * 9 + 1) * 10000));
        shardClerk.setLeaderId(0);
    }

    public ShardConfig query(int num) {
        shardClerk.setSeqId(shardClerk.getSeqId() + 1);
        Args args = new QueryArgs(num);
        args.setClientId(shardClerk.getClientId());
        args.setSeqId(shardClerk.getSeqId());
        args.setType(ShardCommandType.QUERY);
        ShardCommandRequest request = new ShardCommandRequest();
        request.setArgs(args);

        return (ShardConfig) sendCommandRequest(request);
    }

    public void join(Map<Integer, List<Integer>> servers) {
        shardClerk.setSeqId(shardClerk.getSeqId() + 1);
        Args args = new JoinArgs(servers);
        args.setClientId(shardClerk.getClientId());
        args.setSeqId(shardClerk.getSeqId());
        args.setType(ShardCommandType.JOIN);
        ShardCommandRequest request = new ShardCommandRequest();
        request.setArgs(args);

        sendCommandRequest(request);
    }

    public void leave(List<Integer> gids) {
        shardClerk.setSeqId(shardClerk.getSeqId() + 1);
        Args args = new LeaveArgs(gids);
        args.setClientId(shardClerk.getClientId());
        args.setSeqId(shardClerk.getSeqId());
        args.setType(ShardCommandType.LEAVE);
        ShardCommandRequest request = new ShardCommandRequest();
        request.setArgs(args);

        sendCommandRequest(request);
    }

    public void move(int shard, int gid) {
        shardClerk.setSeqId(shardClerk.getSeqId() + 1);
        Args args = new MoveArgs(shard, gid);
        args.setClientId(shardClerk.getClientId());
        args.setSeqId(shardClerk.getSeqId());
        args.setType(ShardCommandType.MOVE);
        ShardCommandRequest request = new ShardCommandRequest();
        request.setArgs(args);

        sendCommandRequest(request);
    }

    public Object sendCommandRequest(ShardCommandRequest request) {
        //
        LocalDateTime time = LocalDateTime.now().plusSeconds(500);
        while(LocalDateTime.now().isBefore(time)) {
            ShardCommandResponse response = RpcUtils.call(shardClerk.getClientId(), shardClerk.getLeaderId(), shardClerk.getServices()[shardClerk.getLeaderId()], "requestCommand", request);
            if(response != null && response.isSuccess()) {
                return response.getValue();
            }

            // 如果失败则遍历servers找到有效的leader
            shardClerk.setLeaderId((shardClerk.getLeaderId() + 1) % shardClerk.getServices().length);
            try {
                Thread.sleep(TimeConstant.RETRY_TIMEOUT);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    public int getId() {
        return shardClerk.getClientId();
    }


}
