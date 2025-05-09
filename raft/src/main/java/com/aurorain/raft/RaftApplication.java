package com.aurorain.raft;

import com.aurorain.raft.config.RaftConfig;
import com.aurorain.myrpc.config.RegistryConfig;
import com.aurorain.myrpc.config.RpcConfig;
import com.aurorain.myrpc.constant.RpcConstant;
import com.aurorain.commonmodule.model.Channel;
import com.aurorain.commonmodule.model.ServiceMetaInfo;
import com.aurorain.raft.model.dto.ApplyMsg;
import com.aurorain.myrpc.proxy.ServiceProxyFactory;
import com.aurorain.myrpc.registry.Registry;
import com.aurorain.myrpc.registry.RegistryFactory;
import com.aurorain.myrpc.serializer.Serializer;
import com.aurorain.myrpc.serializer.SerializerFactory;
import com.aurorain.myrpc.serializer.SerializerKeys;
import com.aurorain.myrpc.server.VertxHttpServer;
import com.aurorain.raft.service.RaftService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * @author aurorain
 * @version 1.0
 */
@Slf4j
public class RaftApplication {

    private RaftConfig config = RaftConfig.getInstance();

    private Registry registry;

    private boolean snapshot;

    private Random random = new Random();

    private static final int SNAPSHOT_INTERVAL = 10;

    /**
     * 初始化 raft 应用，击中管理所有 raft 并提供相关操作方法
     *
     * @param n
     * @param unreliable
     * @param snapshot
     */
    public void init(int n, boolean unreliable, boolean snapshot) {
        config.setRaftCount(n);
        config.setRafts(new Raft[n]);
        config.setConnected(new boolean[n]);
        config.setSaved(new Persister[n]);
        config.setLogs(new HashMap[n]);
        config.setStartTime(LocalDateTime.now());
        config.setServers(new VertxHttpServer[n]);
        config.setPeers(new HashMap<>());
        config.setServices(new ServiceMetaInfo[n]);
        ServiceMetaInfo[] services = config.getServices();
        Map<Integer, RaftService> peers = config.getPeers();
        Map<Integer, Object>[] logs = config.getLogs();
        this.snapshot = snapshot;
        int port = 22222;

        // 1.初始化 serviceMetaInfo 并注册服务
        RpcConfig shardKvRpcConfig = RpcConfig.getRpcConfig();
        RegistryConfig registryConfig = shardKvRpcConfig.getRegistryConfig();
        registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        for (int j = 0; j < n; j++) {
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(RaftService.class.getName());
            serviceMetaInfo.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);
            serviceMetaInfo.setServiceHost(shardKvRpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(port++);
            serviceMetaInfo.setId(j);
            services[j] = serviceMetaInfo;
            logs[j] = new HashMap<>();
            try {
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                throw new RuntimeException("fail to start raft", e);
            }
        }

        // 2.初始化 rpc 服务调用传给所有的 raft
        for (int i = 0; i < n; i++) {
            peers.put(i, ServiceProxyFactory.getProxy(RaftService.class, i, RpcConstant.DEFAULT_SERVICE_VERSION));
        }

        // 3.实例化 raft 分配单例给 server,并启动 server
        for (int i = 0; i < n; i++) {
            startServer(i);
        }

        // 4.初始化 raft (开启其 ticker 线程)
        for (int i = 0; i < n; i++) {
            startRaft(i);
        }
    }

    /**
     * application 自己维护一个 logs map 数组而不直接访问 raft 的 logs
     * 通过多线程读取 raft 通道已提交日志信息来更新 logs
     *
     * @param i
     * @param channel
     */
    public void applier(int i, Channel<ApplyMsg> channel) {
        while (config.getConnected()[i]) {
            // 循环读，有数据则更新，无数据则等待
            ApplyMsg applyMsg = channel.readOne();
            if (applyMsg.isCommandValid()) {
                config.getLock().lock();
                boolean result = checkLogs(i, applyMsg);
                if (!result) {
                    log.error("fail to apply message:{} to server:{}", applyMsg, i);
                }
                config.getLock().unlock();
            }
        }
    }

    /**
     * 周期性的生成 raft state 的快照
     * 与 applier 互斥，在包含 applier 功能的基础上加入了 snapshot 的生成和解析
     *
     * @param i
     * @param channel
     */
    public void applierSnap(int i, Channel<ApplyMsg> channel) {
        int lastApplied = 0;
        final Serializer serializer = SerializerFactory.getInstance(SerializerKeys.KRYO);
        while (config.getConnected()[i]) {
            ApplyMsg applyMsg = channel.readOne();
            if (applyMsg.isSnapShotValid()) {
                config.getLock().lock();
                // 1.raft 接收到 requestInstallSnapshot 请求不会第一时间清空日志
                // 2.而是告知 application，application 清空自己维护的 log 并更新起点
                // 3.由 application 调用 raft 对外暴露的接口(condInstallSnapshot)，间接修改 raft 的日志(即清空或裁切并更新起点)
                // 4.一般情况下 leader 会进入第二个 if 分支生成快照，而 follower 会在 leader 生成快照之后接收到 requestInstallSnapshot 从而进入这个分支并调用 condInstallSnapshot
                // 5.condInstallSnapshot 和 snapshot 效果一致都是更新提交日志后对快照持久化，区别在于调用者不同，以及 snapshot 的原数据来自 leader 的日志不为 null，而 condInstallSnapshot 的数据来源于 request，如果 follower 进入下面的分支会因为 cmd 为 null 而无法序列化
                if (config.getRafts()[i].condInstallSnapshot(applyMsg.getSnapShotTerm(), applyMsg.getSnapShotIndex(), applyMsg.getSnapShot())) {
                    config.getLogs()[i] = new HashMap<>();
                    try {
                        Object o = serializer.deserialize(applyMsg.getSnapShot(), Object.class);
                        config.getLogs()[i].put(applyMsg.getSnapShotIndex(), o);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                config.getLock().unlock();
            } else if (applyMsg.isCommandValid() && applyMsg.getCommandIndex() > lastApplied) {
                config.getLock().lock();
                // 正常提交日志
                boolean result = checkLogs(i, applyMsg);
                config.getLock().unlock();
                if (applyMsg.getCommandIndex() > 1 && !result) {
                    log.error("fail to apply message:{} to server:{}", applyMsg, i);
                }
                lastApplied = applyMsg.getCommandIndex();
                // 提交的日志达到一定个数之后开始生成快照，告知 raft 生成快照裁切和位置和要持久化的命令
                if ((applyMsg.getCommandIndex() + 1) % SNAPSHOT_INTERVAL == 0) {
                    try {
                        byte[] bytes = serializer.serialize(applyMsg.getCommand());
                        config.getRafts()[i].snapshot(applyMsg.getCommandIndex(), bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    /**
     * 检查日志条目和提交内容是否匹配并修改
     *
     * @param i
     * @param applyMsg
     * @return
     */
    public boolean checkLogs(int i, ApplyMsg applyMsg) {
        Object command = applyMsg.getCommand();
        int index = applyMsg.getCommandIndex();
        Map<Integer, Object>[] logs = config.getLogs();
        boolean key = logs[i].containsKey(index - 1);
        logs[i].put(index, command);
        if (index > config.getMaxIndex()) {
            config.setMaxIndex(index);
        }
        return key;
    }

    /**
     * 检查是否只有一个 leader
     *
     * @return
     */
    public int checkOneLeader() {
        for (int i = 0; i < 10; i++) {
            int interval = 450 + random.nextInt(100);
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            Raft[] rafts = config.getRafts();
            Map<Integer, List<Integer>> leaders = new HashMap<>();
            for (int j = 0; j < config.getRaftCount(); j++) {
                if (rafts[j] != null && !rafts[j].killed() && rafts[j].isLeader()) {
                    if (!leaders.containsKey(rafts[j].getTerm())) {
                        leaders.put(rafts[j].getTerm(), new ArrayList<>());
                    }
                    List<Integer> list = leaders.get(rafts[j].getTerm());
                    list.add(j);
                }
            }

            int lastTermLeader = -1;
            for (Map.Entry<Integer, List<Integer>> kv : leaders.entrySet()) {
                int term = kv.getKey();
                if (kv.getValue().size() > 1) {
                    log.error("term {} has {} leaders", term, kv.getValue());
                }
                if (term > lastTermLeader) {
                    lastTermLeader = term;
                }
            }

            if (leaders.size() != 0) {
                return leaders.get(lastTermLeader).get(0);
            }
        }
        log.error("expected one leader, but got none");
        return -1;
    }

    /**
     * 检查是否不存在 leader
     */
    public void checkNoLeader() {
        for (int i = 0; i < config.getRaftCount(); i++) {
            if (config.getConnected()[i] && config.getRafts()[i].isLeader()) {
                log.error("expected no leader but got one:raft{}", i);
            }
        }
    }

    /**
     * 检查是否所有 raft 所处的 term 一致
     *
     * @return
     */
    public int checkTerms() {
        int term = -1;
        int n = config.getRaftCount();
        for (int i = 0; i < n; i++) {
            if (config.getConnected()[i]) {
                int xterm = config.getRafts()[i].getTerm();
                if (term == -1) {
                    term = xterm;
                } else if (xterm != term) {
                    log.error("servers disagree on term");
                }
            }
        }
        return term;
    }

    /**
     * 检查某日志索引的提交数和对应的命令
     *
     * @param index
     * @return
     */
    public Object[] nCommitted(int index) {
        int cnt = 0;
        Object cmd = null;
        int n = config.getRaftCount();
        for (int i = 0; i < n; i++) {
            if (config.getLogs()[i].containsKey(index)) {
                if (cnt > 0 && !config.getLogs()[i].get(index).equals(cmd)) {
                    log.error("committed values do not match index:{}, expected {} but got {}", index, cmd, config.getLogs()[i].get(index));
                }
                cnt++;
                cmd = config.getLogs()[i].get(index);
            }
        }
        return new Object[]{cnt, cmd};
    }

    /**
     * wait for at least n servers to commit.
     *
     * @param index
     * @param n
     * @param startTerm
     * @return
     */
    public Object wait(int index, int n, int startTerm) {
        int to = 10;
        Object[] objects = new Object[2];
        for (int i = 0; i < 30; i++) {
            objects = nCommitted(index);
            if ((int) objects[0] >= n) {
                break;
            }
            try {
                Thread.sleep(to);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (to < 1000) {
                to *= 2;
            }
            if (startTerm > -1) {
                for (Raft raft : config.getRafts()) {
                    if (raft.getTerm() > startTerm) {
                        return -1;
                    }
                }
            }
        }
        if ((int) objects[0] < n) {
            log.error("only {} decided for index {}; wanted {}", objects[0], index, n);
        }
        return objects[1];
    }

    /**
     * 给指定个数的 raft 新增一条日志(实际只给 leader 加，但是会检查是否有指定个 raft 已经追加日志)
     *
     * @param cmd
     * @param expectedServers
     * @param retry
     * @return 返回增加一条日志后该日志对应的索引值
     */
    public int one(Object cmd, int expectedServers, boolean retry) {
        LocalDateTime time1 = LocalDateTime.now().plus(Duration.ofSeconds(10));
        int index = -1;
        while (LocalDateTime.now().isBefore(time1)) {
            int n = config.getRaftCount();
            // 遍历每一个 raft 新增一个日志
            for (int i = 0; i < n && index == -1; i++) {
                config.getLock().lock();
                if (config.getConnected()[i]) {
                    int index1 = config.getRafts()[i].startCmd(cmd);
                    if (index1 != -1) {
                        index = index1;
                        config.getLock().unlock();
                        break;
                    }
                }
                config.getLock().unlock();
            }

            // 如果提交成功
            if (index != -1) {
                LocalDateTime time2 = LocalDateTime.now().plus(Duration.ofSeconds(2));
                while (LocalDateTime.now().isBefore(time2)) {
                    // 获取要求新增的日志有多少 raft 新增成功
                    int cnt = (int) nCommitted(index)[0];
                    Object cmd1 = nCommitted(index)[1];
                    log.info("{} rafts applied cmd:{}, index: {}", cnt, cmd1, index);
                    // 如果成功新增日志的 raft 的个数满足期望的值则返回结束
                    if (cnt >= expectedServers && cmd1.equals(cmd)) {
                        return index;
                    }
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!retry) {
                    log.error("cmd:{} failed to reach agreement", cmd);
                }
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        log.error("cmd:{} failed to reach agreement", cmd);
        return -1;
    }

    /**
     * 重连 raft, raft 没有被销毁
     * 1.connected = true
     * 2.重启监听 apply 线程
     * 3.启动服务器
     * 4.重启 raft,不需要持久化恢复,crash 之后 init raft 需要
     *
     * @param i
     */
    public void connect(int i) {
        if (!config.getConnected()[i]) {
            log.info("connect raft {}", i);
            Channel<ApplyMsg> channel = new Channel<>();
            config.getConnected()[i] = true;
            if (snapshot) {
                new Thread(() -> applierSnap(i, channel)).start();
            } else {
                new Thread(() -> applier(i, channel)).start();
            }
            config.getServers()[i].doStart(config.getServices()[i].getServicePort());
            config.getRafts()[i].restart(channel);
        }
    }

    /**
     * 选择一个 raft 断开连接
     * 1.connected = false
     * 2.关闭 http 服务器
     * 3.执行 raft 的 kill 函数
     *
     * @param i
     */
    public void disconnect(int i) {
        if (config.getConnected()[i]) {
            log.info("disconnect raft:{}", i);
            config.getConnected()[i] = false;
            config.getServers()[i].doShutdown();
            // 通过执行 killed 函数来停掉 raft 的所有线程（线程基于 killed 循环判断）
            config.getRafts()[i].kill();
        }
    }

    /**
     * 退出清除所有的 raft
     */
    public void cleanup() {
        int n = config.getRaftCount();
        // raft 的启动归为两类，都关闭即可正常退出
        // 1.基于 killed() 判断的线程
        // 2.server 线程
        for (int i = 0; i < n; i++) {
            config.getRafts()[i].kill();
            config.getServers()[i].doShutdown();
            // 注册也要解除一下，因为线程关闭后要等 30 秒才会自己删除
            registry.unRegister(config.getServices()[i]);
        }
    }

    /**
     * crash 对应 startOne
     * disconnect 对应 connect
     * 1.disconnect
     * 2.raft = null
     * 3.重置 persister
     *
     * @param i
     */
    public void crash(int i) {
        disconnect(i);

        config.getLock().lock();

        if (config.getSaved()[i] != null) {
            config.getSaved()[i] = config.getSaved()[i].clone();
        }

        Raft raft = config.getRafts()[i];
        if (raft != null) {
            config.getRafts()[i] = null;
            log.info("raft {} crashed", i);
        }

        if (config.getSaved()[i] != null) {
            byte[] raftState = config.getSaved()[i].readRaftState();
            byte[] snapshot = config.getSaved()[i].readSnapshot();
            config.getSaved()[i] = new Persister();
            config.getSaved()[i].saveRaftStateAndSnapshot(raftState, snapshot);
        }

        config.getLock().unlock();
    }

    /**
     * 启动一个 raft 服务，开启服务器
     *
     * @param i
     */
    public void startServer(int i) {
        crash(i);
        config.getLock().lock();

        if (config.getSaved()[i] == null) {
            config.getSaved()[i] = new Persister();
        }
        ServiceMetaInfo[] services = config.getServices();

        Raft raft = new Raft();
        config.getRafts()[i] = raft;
        VertxHttpServer server = new VertxHttpServer(raft);
        server.doStart(services[i].getServicePort());
        config.getServers()[i] = server;

        config.getLock().unlock();
    }

    /**
     * 用于第一次启动一个 raft
     *
     * @param i
     */
    public void startRaft(int i) {
        Channel<ApplyMsg> channel = new Channel<>();
        config.getLock().lock();
        config.getConnected()[i] = true;
        Raft[] rafts = config.getRafts();
        rafts[i].init(config.getPeers(), i, config.getSaved()[i], channel, false);

        if (snapshot) {
            new Thread(() -> applierSnap(i, channel)).start();
        } else {
            new Thread(() -> applier(i, channel)).start();
        }
        config.getLock().unlock();
    }

    /**
     * 恢复 crash 的 raft
     * 和 connect 不同的是调用这个函数要求 raft 非正常退出，raft == null
     *
     * @param i
     */
    public void startOne(int i) {
        if (!config.getConnected()[i]) {
            Channel<ApplyMsg> channel = new Channel<>();
            config.getLock().lock();
            config.getConnected()[i] = true;

            if (config.getSaved()[i] == null) {
                config.getSaved()[i] = new Persister();
            }
            ServiceMetaInfo[] services = config.getServices();

            Raft raft = new Raft();
            config.getRafts()[i] = raft;
            VertxHttpServer server = new VertxHttpServer(raft);
            raft.init(config.getPeers(), i, config.getSaved()[i], channel, false);
            raft.resetElectionTimer();

            if (snapshot) {
                new Thread(() -> applierSnap(i, channel)).start();
            } else {
                new Thread(() -> applier(i, channel)).start();
            }

            raft.resetElectionTimer();
            server.doStart(services[i].getServicePort());
            config.getServers()[i] = server;

            config.getLock().unlock();
            log.info("start raft {} from crashing", i);
        }
    }

    /**
     * Maximum log size across all servers
     *
     * @return
     */
    public int logByteSize() {
        int logsize = 0;
        for (int i = 0; i < config.getRaftCount(); i++) {
            int n = config.getSaved()[i].raftStateSize();
            if (n > logsize) {
                logsize = n;
            }
        }
        return logsize;
    }

    /**
     * 获取 logs map 存储的日志数量
     *
     * @return
     */
    public int logSize() {
        int logsize = 0;
        for (int i = 0; i < config.getRaftCount(); i++) {
            int n = config.getLogs()[i].size();
            if (n > logsize) {
                logsize = n;
            }
        }
        return logsize;
    }

    /**
     * 获取某一个 raft
     *
     * @param i
     * @return
     */
    public Raft getRaft(int i) {
        return config.getRafts()[i];
    }

    public Lock getLock() {
        return config.getLock();
    }

}
