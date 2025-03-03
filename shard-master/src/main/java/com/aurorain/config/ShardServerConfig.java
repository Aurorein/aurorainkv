package com.aurorain.config;

import com.aurorain.ShardMasterServer;
import com.aurorain.service.ShardServerService;
import com.aurorain.Persister;
import com.aurorain.model.ServiceMetaInfo;
import com.aurorain.server.VertxHttpServer;
import com.aurorain.service.RaftService;
import lombok.Data;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Data
public class ShardServerConfig {

    /**
     * 单例
     */
    private static ShardServerConfig config = new ShardServerConfig();

    private Lock lock = new ReentrantLock();

    private ShardMasterServer[] shardMasterServers;

    private ShardServerService[] shardServerServices;

    private RaftService[] peers;

    private VertxHttpServer[] raftServers;

    private VertxHttpServer[] kvRaftServers;

    private ServiceMetaInfo[] raftServiceInfos;

    private ServiceMetaInfo[] kvRaftServiceInfos;

    private int count;

    private Persister[] saved;

    private int nextClientId;

    private int maxRaftState;

    public static ShardServerConfig getConfig() {
        return config;
    }



}
