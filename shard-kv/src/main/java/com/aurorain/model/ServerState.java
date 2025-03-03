package com.aurorain.model;

import com.aurorain.Client;
import com.aurorain.Raft;
import com.aurorain.ShardClient;
import com.aurorain.ShardConfig;
import com.aurorain.common.CommandContext;
import com.aurorain.model.dto.ApplyMsg;
import com.aurorain.model.dto.CommandResponse;
import com.aurorain.store.KV;
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
    private Lock lock = new ReentrantLock();

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
    private Channel<ApplyMsg> channel;

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
    private KV store;

    /**
     * 用于某个时期临时的 response 通讯，applier 告知 handler 从 raft 获取的命令生成的 response，handler 会在返回 response 之前销毁通道
     */
    private Map<ShardIndexAndTerm, Channel<CommandResponse>> cmdResponseChannels;

    /**
     * clientId 对应的最后一次提交命令的上下文信息
     */
    private Map<Integer, CommandContext> lastCmdContext;

    /**
     * 最后一次 raft 向 kv 提交条目的索引
     */
    private int lastApplied;

    /**
     * 最后一次生成快照的位置
     */
    private int lastSnapshot;

    // shardKv新加的

    private ShardClient shardClient;

    private ShardConfig shardConfig;

    private int gid;

    private Client client;

    private HashSet<Integer> availableShards;

    // 需要送出去的分片 configNum -> shard -> (k, v)
    private HashMap<Integer, HashMap<Integer, HashMap<String, String>>> toOutShards;

    // 需要拉取的分片 shard -> configNum
    private HashMap<Integer, Integer> comeInShards;

    private HashMap<Integer, HashSet<Integer>> garbages;

    private HashMap<Integer, HashSet<Integer>> garbagesBackUp;

    private int garbageFinished;

    private int garbageConfigNum;

    private int pullShardFinished;

    private Iterator<Integer> garbageIter;

    private Iterator<Map.Entry<Integer, Integer>> pullShardIter;

}
