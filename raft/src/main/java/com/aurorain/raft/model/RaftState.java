package com.aurorain.raft.model;

import com.aurorain.raft.Persister;
import com.aurorain.raft.constant.RaftConstant;
import com.aurorain.commonmodule.model.Channel;
import com.aurorain.raft.model.dto.ApplyMsg;
import com.aurorain.raft.service.RaftService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * rafe state
 *
 * @author aurorain
 * @version 1.0
 */
@Data
public class RaftState {
    @JsonIgnore
    private transient ReentrantLock lock = new ReentrantLock();  // 互斥锁
    @JsonIgnore
    private transient Persister persister; // 持久化对象
    @JsonIgnore
    private transient Map<Integer, RaftService> peers; // 集群节点的调用服务
    private int me; // 自身标识
    private AtomicInteger dead = new AtomicInteger(0); // 是否宕机
    private int currentTerm = 0; // 当前任期（持久化对象）
    private int votedFor = -1; // 当前任期投票的候选者id（持久化对象）
    private Entry[] logs; // 日志（持久化对象）
    @JsonIgnore
    private transient Condition applyCond = lock.newCondition();
    @JsonIgnore
    private transient Channel<ApplyMsg> channel; // 与KV层的通信
    @JsonIgnore
    private transient Condition[] replicatorCond;
    private int state = RaftConstant.FOLLOWER; // raft节点状态（leader、candidate、follower）
    private int commitIndex = 0; // 日志提交的最大下标
    private int lastApplied = 0; // 日志应用的最大下标
    private Map<Integer, Integer> nextIndex = new HashMap<>(); // id -> 下一个要发送的日志
    private Map<Integer, Integer> matchIndex = new HashMap<>(); // id -> 已经复制成功的日志
    private LocalDateTime electionTimer; // 发起选举的超时时间
    private LocalDateTime heartbeatTimer; // 心跳超时时间

    private boolean isShardMaster;
}
