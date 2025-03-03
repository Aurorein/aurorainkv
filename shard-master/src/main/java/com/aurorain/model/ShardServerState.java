package com.aurorain.model;

import com.aurorain.ShardConfig;
import com.aurorain.common.ShardCommandContext;
import com.aurorain.model.dto.dto.ShardCommandResponse;
import com.aurorain.Raft;
import com.aurorain.model.dto.ApplyMsg;
import lombok.Data;
import java.util.List;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class ShardServerState {
    /**
     * 锁
     */
    private Lock lock = new ReentrantLock();

    private int me;

    private Raft raft;

    /**
     * raft与应用层通信
     */
    private Channel<ApplyMsg> channel;
    private AtomicInteger dead = new AtomicInteger(0);

    // TODO
    private int maxRaftState;

    /**
     * 用于某个时期临时的 response 通讯，applier 告知 handler 从 raft 获取的命令生成的 response，handler 会在返回 response 之前销毁通道
     */
    private Map<ShardIndexAndTerm, Channel<ShardCommandResponse>> cmdResponseChannels;

    /**
     * clientId 对应的最后一次提交命令的上下文信息
     */
    private Map<Integer, ShardCommandContext> lastCmdContext;

    /**
     * 最后一次 raft 向 kv 提交条目的索引
     */
    private int lastApplied;

    private List<ShardConfig> configs;

    public ShardConfig getConfigByIndex(int idx) {
        if(idx < 0 || idx >= configs.size()) {
            return configs.get(configs.size() - 1).clone();
        } else {
            return configs.get(idx).clone();
        }
    }

}
