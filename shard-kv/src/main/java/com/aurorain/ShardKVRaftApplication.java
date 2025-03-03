package com.aurorain;

import com.aurorain.config.KVRaftConfig;
import com.aurorain.config.RegistryConfig;
import com.aurorain.config.RpcConfig;
import com.aurorain.constant.RpcConstant;
import com.aurorain.model.ServiceMetaInfo;
import com.aurorain.proxy.ServiceProxyFactory;
import com.aurorain.registry.Registry;
import com.aurorain.registry.RegistryFactory;
import com.aurorain.server.VertxHttpServer;
import com.aurorain.service.KVServerService;
import com.aurorain.service.RaftService;
import com.aurorain.utils.RpcUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Random;
import java.util.stream.IntStream;

/**
 * @author aurorain
 * @version 1.0
 */
@Slf4j
public class ShardKVRaftApplication {

    private final KVRaftConfig config = KVRaftConfig.getConfig();

    private static final int RAFT_PORT = 22222;

    private static final int KV_SERVER_PORT = 8888;

    private Registry registry;

    public KVRaftConfig init(int[] ids, int gid, int maxRaftState, ShardClient shardClient, Client client) {
        int n = ids.length;
        config.setIds(ids);
        config.setCount(ids.length);
        config.setMaxRaftState(maxRaftState);
        config.setSaved(new HashMap<>());
        for(int id : ids) config.getSaved().put(id, new Persister());
        config.setNextClientId(n + 1000);
        config.setKvServerServices(new HashMap<>());
        config.setRaftServiceInfos(new HashMap<>());
        config.setKvRaftServers(new HashMap<>());
        config.setShardKvServers(new HashMap<>());
        config.setKvRaftServiceInfos(new HashMap<>());
        config.setKvRaftServers(new HashMap<>());
        config.setRaftServers(new HashMap<>());
        config.setPeers(new HashMap<>());
        config.setGid(gid);
        config.setClient(client);

        RpcConfig rpcConfig = RpcConfig.getRpcConfig();
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        registry = RegistryFactory.getInstance(registryConfig.getRegistry());

        // 1.注册 Raft 和 KVServer 服务
        for (int id : ids) {
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(RaftService.class.getName());
            serviceMetaInfo.setServiceVersion(gid + RpcConstant.SHARDKV_SERVICE_VERSION);
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(RAFT_PORT + id + gid * 1111);
            serviceMetaInfo.setId(id);
            config.getRaftServiceInfos().put(id, serviceMetaInfo);
            ServiceMetaInfo serviceMetaInfo1 = new ServiceMetaInfo();
            serviceMetaInfo1.setServiceName(KVServerService.class.getName());
            serviceMetaInfo1.setServiceVersion(gid + RpcConstant.SHARDKV_SERVICE_VERSION);
            serviceMetaInfo1.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo1.setServicePort(KV_SERVER_PORT + id + gid * 1111);
            serviceMetaInfo1.setId(id);
            config.getKvRaftServiceInfos().put(id, serviceMetaInfo1);
            try {
                registry.register(serviceMetaInfo);
                registry.register(serviceMetaInfo1);
            } catch (Exception e) {
                throw new RuntimeException("fail to start raft", e);
            }
        }

        // 2.获取服务调用
        for (int id : ids) {
            config.getPeers().put(id, ServiceProxyFactory.getProxy(RaftService.class, id, gid + RpcConstant.SHARDKV_SERVICE_VERSION));
            config.getKvServerServices().put(id, ServiceProxyFactory.getProxy(KVServerService.class, id, gid + RpcConstant.SHARDKV_SERVICE_VERSION));
        }

        // 3.启动服务器
        for (int id : ids) {
            startServer(id, gid, shardClient);
        }

        addToClient(client);

        // 4.连接一切
//        connectAll();
        // for test
        return config;
    }

    public void addToClient(Client client) {
        client.addGroup(config.getGid(), this.config.getKvServerServices());
    }

    /**
     * 将 kvServers 分为两组，并将 leader 置于较小的一组当中
     *
     * @return p1 为 majority，p2 为 minority
     */
    public int[][] makePartition() {
        int n = config.getCount();
        int leader = getLeader();
        int[][] ret = new int[2][];
        ret[0] = new int[n / 2 + 1];
        ret[1] = new int[n / 2];
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i != leader) {
                if (j < ret[0].length) {
                    ret[0][j] = i;
                } else {
                    ret[1][j - ret[0].length] = i;
                }
                j++;
            }
        }
        ret[1][ret[1].length - 1] = leader;
        return ret;
    }

    /**
     * 根据 p1 和 p2 两个数组制造分区
     *
     * @param p1 第一个分区
     * @param p2 第二个分区
     */
    public void partition(int[] p1, int[] p2) {
        config.getLock().lock();
        for (int i = 0; i < p1.length; i++) {
            disconnectUnlocked(p1[i], p2);
            connectUnlocked(p1[i], p1);
        }
        for (int i = 0; i < p2.length; i++) {
            disconnectUnlocked(p2[i], p1);
            connectUnlocked(p2[i], p2);
        }
        config.getLock().unlock();
    }

    /**
     * 启动一个 kvServer 服务端
     * 包含 raft httpserver 和 kv httpserver
     *
     * @param server
     */
    public void startServer(int server, int gid, ShardClient shardClient) {
        config.getLock().lock();
        if (!config.getSaved().containsKey(server)) {
            config.getSaved().put(server, config.getSaved().get(server).clone());
        } else {
            config.getSaved().put(server, new Persister());
        }
        config.getLock().unlock();

        ShardKVServer shardKvServer = new ShardKVServer();
        shardKvServer.getState().setClient(config.getClient());
        shardKvServer.getState().setShardClient(shardClient);
        config.getRaftServers().put(server, shardKvServer.init(server, gid, config.getPeers(), config.getSaved().get(server), config.getMaxRaftState()));
        config.getKvRaftServers().put(server, new VertxHttpServer(shardKvServer));
        config.getShardKvServers().put(server, shardKvServer);
        config.getKvRaftServers().get(server).doStart(config.getKvRaftServiceInfos().get(server).getServicePort());
        config.getRaftServers().get(server).doStart(config.getRaftServiceInfos().get(server).getServicePort());


        connectUnlocked(server, all());
    }

    /**
     * 与 startServer 相对，用于完整关闭一个 kvServer
     *
     * @param server
     */
    public void shutdownServer(int server) {
        config.getLock().lock();
        disconnectUnlocked(server, all());
        config.getRaftServers().get(server).doShutdown();
        config.getKvRaftServers().get(server).doShutdown();
        log.info("shutdown kv server {}", server);

        Persister persister = config.getSaved().get(server);
        if (persister != null) {
            config.getSaved().put(server, persister.clone());
        }

        ShardKVServer shardKvServer = config.getShardKvServers().get(server);
        if (shardKvServer != null) {
            config.getLock().unlock();
            shardKvServer.kill();
            config.getLock().lock();
            config.getShardKvServers().put(server, null);
        }
        config.getLock().unlock();
    }

    /**
     * 以不上锁的形式启用某个 raft 和其他集群之间互相的调用权限
     *
     * @param i
     * @param to
     */
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

    /**
     * 构建一个 client
     *
     * @return
     */
//    public Client makeClient(ShardKVServer server) {
//        config.getLock().lock();
//        Client client = new Client(config.getKvServerServices(), server);
//        config.setNextClientId(config.getNextClientId() + 1);
//        config.getLock().unlock();
//        return client;
//    }

    /**
     * 以不上锁的形式启用该 client 对 kvServer 分组的调用权限
     *
     * @param shardClient
     * @param to
     */
    public void connectClientUnlocked(ShardClient shardClient, int[] to) {
        for (int x : to) {
            RpcUtils.enable(KVServerService.class, Integer.valueOf(shardClient.getId()), x);
        }
    }

    public void connectClient(ShardClient shardClient, int[] to) {
        config.getLock().lock();
        connectClientUnlocked(shardClient, to);
        config.getLock().unlock();
    }

    /**
     * 以不上锁的形式停用该 client 对 kvServer 分组的调用权限
     *
     * @param shardClient
     * @param from
     */
    public void disconnectClientUnlocked(ShardClient shardClient, int[] from) {
        for (int x : from) {
            RpcUtils.disable(KVServerService.class, shardClient.getId(), x);
        }
    }

    public void disconnectClient(ShardClient shardClient, int[] from) {
        config.getLock().lock();
        disconnectClientUnlocked(shardClient, from);
        config.getLock().unlock();
    }

    public int logSize() {
        int logsize = 0;
        for (int id : config.getIds()) {
            int n = config.getSaved().get(id).raftStateSize();
            if (n > logsize) {
                logsize = n;
            }
        }
        return logsize;
    }

    public int snapshotSize() {
        int snapshotSize = 0;
        for (int id : config.getIds()) {
            int n = config.getSaved().get(id).snapshotSize();
            if (n > snapshotSize) {
                snapshotSize = n;
            }
        }
        return snapshotSize;
    }

    public int getNServers() {
        return config.getCount();
    }

    public int getLeader() {
        for (int id : config.getIds()) {
            if (config.getShardKvServers().get(id).isLeader()) {
                return id;
            }
        }
        return -1;
    }

    public void cleanup() {
        int n = config.getCount();
        for (int id : config.getIds()) {
            config.getShardKvServers().get(id).kill();
            config.getRaftServers().get(id).doShutdown();
            config.getKvRaftServers().get(id).doShutdown();
            registry.unRegister(config.getKvRaftServiceInfos().get(id));
            registry.unRegister(config.getRaftServiceInfos().get(id));
        }
    }

}
