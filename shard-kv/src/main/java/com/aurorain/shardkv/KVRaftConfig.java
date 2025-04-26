package com.aurorain.shardkv;

import com.aurorain.raft.Persister;
import com.aurorain.commonmodule.model.ServiceMetaInfo;
import com.aurorain.myrpc.server.VertxHttpServer;
import com.aurorain.shardkv.service.KVServerService;
import com.aurorain.raft.service.RaftService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * KVServer 配置
 *
 * @author aurorain
 * @version 1.0
 */
@Data
public class KVRaftConfig implements Serializable {

    /**
     * 单例
     */
//    private static KVRaftConfig config = new KVRaftConfig();

    /**
     * 锁
     */
    @JsonIgnore
    private transient Lock lock = new ReentrantLock();

    /**
     * KVServer 实例数组
     */
    private Map<Integer, ShardKVServer> shardKvServers;

    /**
     * KVServer 服务调用数组
     */
    @JsonIgnore
    private transient Map<Integer, KVServerService> kvServerServices;

    /**
     * raft service 服务调用数组
     */
    @JsonIgnore
    private transient Map<Integer, RaftService> peers;

    /**
     * raft httpserver
     */
    @JsonIgnore
    private transient Map<Integer, VertxHttpServer> raftServers;

    /**
     * kv raft httpserver
     */
    @JsonIgnore
    private transient Map<Integer, VertxHttpServer> kvRaftServers;

    /**
     * raft 服务信息
     */
    private Map<Integer, ServiceMetaInfo> raftServiceInfos;

    /**
     * KVServer 服务信息
     */
    private Map<Integer, ServiceMetaInfo> kvRaftServiceInfos;

    /**
     * server 数量
     */
    private int count;

    /**
     * raft 的持久化对象数组
     */
    @JsonIgnore
    private transient Map<Integer, Persister> saved;

    /**
     * 记录生成的 client 个数
     */
    private int nextClientId;

    /**
     * snapshot 模式下裁切允许的最大容量
     */
    private int maxRaftState;

    private int gid;

    private int[] ids;

    @JsonIgnore
    private transient Client client;

    public static KVRaftConfig getConfig() {
        return new KVRaftConfig();
    }

}
