package com.aurorain.shardkv.model;

import com.aurorain.shardkv.Client;
import com.aurorain.raft.Raft;
import com.aurorain.shardkv.common.CommandContext;
import com.aurorain.shardkv.transaction.Latches;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardmaster.ShardConfig;
import com.aurorain.raft.model.dto.ApplyMsg;
import com.aurorain.commonmodule.model.Channel;
import com.aurorain.shardmaster.model.ShardIndexAndTerm;
import com.aurorain.shardkv.model.dto.CommandResponse;
import com.aurorain.shardkv.store.KV;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * KVServer 配置
 *
 * @author aurorain
 * @version 1.0
 */
@Data
public class ServerState {

    /**
     * 锁
     */
    @JsonIgnore
    private transient Lock lock = new ReentrantLock();

    /**
     * 自身标识
     */
    private int me;

    /**
     * 该 server 所使用的 raft
     */
    private Raft raft;

    /**
     * 用于 raft 与上层的 kv 通信
     */
    @JsonIgnore
    private transient Channel<ApplyMsg> channel;

    /**
     * 状态标识，原子化
     */
    private AtomicInteger dead = new AtomicInteger(0);

    /**
     * 所能存储的最大 raft 状态，snapshot 模式下使用
     */
    private int maxRaftState;

    /**
     * 该 server 所使用的 kv 存储对象
     */
    @JsonIgnore
    private transient KV store;

    /**
     * 用于某个时期临时的 response 通讯，applier 告知 handler 从 raft 获取的命令生成的 response，handler 会在返回 response 之前销毁通道
     */
    @JsonIgnore
    private transient Map<ShardIndexAndTerm, Channel<CommandResponse>> cmdResponseChannels;

    /**
     * clientId 对应的最后一次提交命令的上下文信息
     */
    @JsonIgnore
    private transient Map<Integer, CommandContext> lastCmdContext;

    /**
     * 最后一次 raft 向 kv 提交条目的索引
     */
    private int lastApplied;

    /**
     * 最后一次生成快照的位置
     */
    private int lastSnapshot;

    // shardKv新加的
    @JsonIgnore
    private transient ShardClient shardClient;

    @JsonIgnore
    private transient ShardConfig shardConfig;

    private int gid;

    @JsonIgnore
    private transient Client client;

    @JsonIgnore
    private transient HashSet<Integer> availableShards;

    // 需要送出去的分片 configNum -> shard -> (k, v)
    @JsonIgnore
    private transient HashMap<Integer, HashMap<Integer, HashMap<String, String>>> toOutShards;

    @JsonIgnore
    // 需要拉取的分片 shard -> configNum
    private transient HashMap<Integer, Integer> comeInShards;

    @JsonIgnore
    private transient HashMap<Integer, HashSet<Integer>> garbages;

    @JsonIgnore
    private transient HashMap<Integer, HashSet<Integer>> garbagesBackUp;

    @JsonIgnore
    private transient int garbageFinished;

    @JsonIgnore
    private transient int garbageConfigNum;

    @JsonIgnore
    private transient int pullShardFinished;

    @JsonIgnore
    private transient Iterator<Integer> garbageIter;

    @JsonIgnore
    private transient Iterator<Map.Entry<Integer, Integer>> pullShardIter;

    @JsonIgnore
    private transient Latches latches;

}
