package com.aurorain.shardmaster;

import com.aurorain.raft.Persister;
import com.aurorain.myrpc.config.RpcConfig;
import com.aurorain.shardmaster.config.ShardServerConfig;
import com.aurorain.shardmaster.service.ShardServerService;
import com.aurorain.myrpc.config.RegistryConfig;
import com.aurorain.myrpc.constant.RpcConstant;
import com.aurorain.commonmodule.model.ServiceMetaInfo;
import com.aurorain.myrpc.proxy.ServiceProxyFactory;
import com.aurorain.myrpc.registry.Registry;
import com.aurorain.myrpc.registry.RegistryFactory;
import com.aurorain.myrpc.server.VertxHttpServer;
import com.aurorain.raft.service.RaftService;
import com.aurorain.commonmodule.utils.RpcUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

public class ShardMasterApplication {
    private final ShardServerConfig config = ShardServerConfig.getConfig();

    private static final int RAFT_PORT = 33333;

    private static final int KV_SERVER_PORT = 7777;

    private Registry registry;

    public ShardServerConfig init(int n, int maxRaftState) {
        config.setCount(n);
        config.setMaxRaftState(maxRaftState);
        config.setSaved(new Persister[n]);
        config.setNextClientId(n + 1000);
        config.setShardServerServices(new ShardServerService[n]);
        config.setRaftServiceInfos(new ServiceMetaInfo[n]);
        config.setShardMasterServers(new ShardMasterServer[n]);
        config.setKvRaftServiceInfos(new ServiceMetaInfo[n]);
        config.setKvRaftServers(new VertxHttpServer[n]);
        config.setRaftServers(new VertxHttpServer[n]);
        config.setPeers(new RaftService[n]);

        RpcConfig shardMasterRpcConfig = RpcConfig.getRpcConfig();
        RegistryConfig registryConfig = shardMasterRpcConfig.getRegistryConfig();
        registry = RegistryFactory.getInstance(registryConfig.getRegistry());

        for(int i = 0; i < n; ++i) {
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(RaftService.class.getName());
            serviceMetaInfo.setServiceVersion(RpcConstant.SHARDMASTER_SERVICE_VERSION);
            serviceMetaInfo.setServiceHost(shardMasterRpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(RAFT_PORT + i);
            serviceMetaInfo.setId(i);
            config.getRaftServiceInfos()[i] = serviceMetaInfo;
            ServiceMetaInfo serviceMetaInfo1 = new ServiceMetaInfo();
            serviceMetaInfo1.setServiceName(ShardServerService.class.getName());
            serviceMetaInfo1.setServiceVersion(RpcConstant.SHARDMASTER_SERVICE_VERSION);
            serviceMetaInfo1.setServiceHost(shardMasterRpcConfig.getServerHost());
            serviceMetaInfo1.setServicePort(KV_SERVER_PORT + i);
            serviceMetaInfo1.setId(i);
            config.getKvRaftServiceInfos()[i] = serviceMetaInfo1;
            try {
                registry.register(serviceMetaInfo);
                registry.register(serviceMetaInfo1);
            } catch (Exception e) {
                throw new RuntimeException("fail to start raft", e);
            }

        }

        for(int i = 0; i < n; ++i) {
            config.getPeers()[i] = ServiceProxyFactory.getProxy(RaftService.class, i, RpcConstant.SHARDMASTER_SERVICE_VERSION);
            config.getShardServerServices()[i] = ServiceProxyFactory.getProxy(ShardServerService.class, i, RpcConstant.SHARDMASTER_SERVICE_VERSION);
        }

        // 3.启动服务器
        for (int i = 0; i < n; i++) {
            startServer(i);
        }

        // 4.连接一切
        connectAll();

        return config;
    }

    public void startServer(int server) {
        config.getLock().lock();

        if(config.getSaved()[server] != null) {
            config.getSaved()[server] = config.getSaved()[server].clone();
        } else {
            config.getSaved()[server] = new Persister();
        }
        config.getLock().unlock();


        Map<Integer, RaftService> map = new HashMap<>();
        for (int i = 0; i < config.getPeers().length; i++) {
            map.put(i, config.getPeers()[i]);
        }

        ShardMasterServer shardMasterServer = new ShardMasterServer();
        config.getRaftServers()[server] = shardMasterServer.init(server,map, config.getSaved()[server], config.getMaxRaftState());
        config.getKvRaftServers()[server] = new VertxHttpServer(shardMasterServer);
        config.getShardMasterServers()[server] = shardMasterServer;
        config.getKvRaftServers()[server].doStart(config.getKvRaftServiceInfos()[server].getServicePort());
        config.getRaftServers()[server].doStart(config.getRaftServiceInfos()[server].getServicePort());

        connectUnlocked(server, all());
    }

    public void connectUnlocked(int i, int[] to) {
        for (int j = 0; j < to.length; j++) {
            RpcUtils.enable(RaftService.class, i, to[j]);
            RpcUtils.enable(RaftService.class, to[j], i);
        }
    }

    public void connect(int i, int[] to) {
        config.getLock().lock();
        connectUnlocked(i, to);
        config.getLock().unlock();
    }

    /**
     * 以不上锁的形式停用某个 raft 和其他集群之间互相的调用权限
     *
     * @param i
     * @param from
     */
    public void disconnectUnlocked(int i, int[] from) {
        for (int j = 0; j < from.length; j++) {
            RpcUtils.disable(RaftService.class, i, from[j]);
            RpcUtils.disable(RaftService.class, from[j], i);
        }
    }

    public void disconnect(int i, int[] from) {
        config.getLock().lock();
        disconnectUnlocked(i, from);
        config.getLock().unlock();
    }

    /**
     * 启用所有 raft 之间的调用权限
     */
    public void connectAll() {
        config.getLock().lock();
        for (int i = 0; i < config.getCount(); i++) {
            connectUnlocked(i, all());
        }
        config.getLock().unlock();
    }

    public int[] all() {
        return IntStream.range(0, config.getCount()).toArray();
    }

    public ShardClient makeClient() {
        config.getLock().lock();
        ShardClient shardClient = new ShardClient(config.getShardServerServices());
        config.setNextClientId(config.getNextClientId() + 1);
        config.getLock().unlock();
        return shardClient;
    }

    public void connectClientUnlocked(ShardClient shardClient, int[] to) {
        for(int x : to) {
            RpcUtils.enable(ShardMasterServer.class, Integer.valueOf(shardClient.getId()), x);
        }
    }

    public void connectClient(ShardClient shardClient, int[] to) {
        config.getLock().lock();
        connectClientUnlocked(shardClient, to);
        config.getLock().unlock();
    }

    public void disconnectClientUnlocked(ShardClient shardClient, int[] from) {
        for (int x : from) {
            RpcUtils.disable(ShardServerService.class, shardClient.getId(), x);
        }
    }

    public int logSize() {
        int logSize = 0;
        for(int i = 0; i < config.getCount(); ++i) {
            int n = config.getSaved()[i].raftStateSize();
            if(n > logSize) {
                logSize = n;
            }
        }
        return logSize;
    }

    public int getNServers() {
        return config.getCount();
    }

    public int getLeader() {
        for (int i = 0; i < config.getCount(); i++) {
            if (config.getShardMasterServers()[i].isLeader()) {
                return i;
            }
        }
        return -1;
    }

    public void cleanup() {
        int n = config.getCount();
        for (int i = 0; i < n; i++) {
            config.getShardMasterServers()[i].kill();
            config.getRaftServers()[i].doShutdown();
            config.getKvRaftServers()[i].doShutdown();
            registry.unRegister(config.getKvRaftServiceInfos()[i]);
            registry.unRegister(config.getRaftServiceInfos()[i]);
        }
    }
}
