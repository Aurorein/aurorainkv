package com.aurorain.config;

import com.aurorain.Client;
import com.aurorain.ShardKVServer;
import com.aurorain.Persister;
import com.aurorain.model.ServiceMetaInfo;
import com.aurorain.server.VertxHttpServer;
import com.aurorain.service.KVServerService;
import com.aurorain.service.RaftService;
import lombok.Data;

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
public class KVRaftConfig {

    /**
     * 单例
     */
//    private static KVRaftConfig config = new KVRaftConfig();

    /**
     * 锁
     */
    private Lock lock = new ReentrantLock();

    /**
     * KVServer 实例数组
     */
    private Map<Integer, ShardKVServer> shardKvServers;

    /**
     * KVServer 服务调用数组
     */
    private Map<Integer, KVServerService> kvServerServices;

    /**
     * raft service 服务调用数组
     */
    private Map<Integer, RaftService> peers;

    /**
     * raft httpserver
     */
    private Map<Integer, VertxHttpServer> raftServers;

    /**
     * kv raft httpserver
     */
    private Map<Integer, VertxHttpServer> kvRaftServers;

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
    private Map<Integer, Persister> saved;

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

    private Client client;

    public static KVRaftConfig getConfig() {
        return new KVRaftConfig();
    }

}
