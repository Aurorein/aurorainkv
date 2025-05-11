package com.aurorain.shardmaster.config;

import com.aurorain.commonmodule.model.ServiceMetaInfo;
import com.aurorain.myrpc.server.VertxHttpServer;
import com.aurorain.raft.Persister;
import com.aurorain.raft.service.RaftService;
import com.aurorain.shardmaster.ShardMasterServer;
import com.aurorain.shardmaster.service.ShardServerService;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class ShardServerConfig implements Serializable {

    /**
     * 单例
     */
    private static ShardServerConfig config = new ShardServerConfig();

    @JsonIgnore
    private Lock lock = new ReentrantLock();

    private ShardMasterServer[] shardMasterServers;

    @JsonIgnore
    private transient ShardServerService[] shardServerServices;

    @JsonIgnore
    private transient RaftService[] peers;

    @JsonIgnore
    private transient VertxHttpServer[] raftServers;

    @JsonIgnore
    private transient VertxHttpServer[] kvRaftServers;

    private ServiceMetaInfo[] raftServiceInfos;

    private ServiceMetaInfo[] kvRaftServiceInfos;

    private int count;

    @JsonIgnore
    private transient Persister[] saved;

    private int nextClientId;

    private int maxRaftState;

    public static ShardServerConfig getConfig() {
        return config;
    }



}
