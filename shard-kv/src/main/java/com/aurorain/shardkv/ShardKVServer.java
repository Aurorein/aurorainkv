package com.aurorain.shardkv;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ArrayUtil;
import com.aurorain.commonmodule.model.Channel;
import com.aurorain.raft.Persister;
import com.aurorain.raft.Raft;
import com.aurorain.raft.model.dto.ApplyMsg;
import com.aurorain.shardkv.constant.ActionConstants;
import com.aurorain.shardkv.constant.CFConstants;
import com.aurorain.shardkv.constant.TimeConstant;
import com.aurorain.shardkv.constant.WriteConstants;
import com.aurorain.shardkv.model.*;
import com.aurorain.shardkv.model.dto.*;
import com.aurorain.shardkv.transaction.*;
import com.aurorain.shardkv.transaction.Scanner;
import com.aurorain.shardmaster.ShardClient;
import com.aurorain.shardmaster.ShardConfig;
import com.aurorain.shardkv.common.CommandContext;
import com.aurorain.shardkv.common.CommandType;
import com.aurorain.shardmaster.model.*;
import com.aurorain.myrpc.serializer.Serializer;
import com.aurorain.myrpc.serializer.SerializerFactory;
import com.aurorain.myrpc.serializer.SerializerKeys;
import com.aurorain.myrpc.server.VertxHttpServer;
import com.aurorain.shardkv.service.KVServerService;
import com.aurorain.raft.service.RaftService;
import com.aurorain.shardkv.store.RocksDBEntry;
import com.aurorain.shardkv.store.RocksDBKV;
import com.aurorain.shardkv.store.RocksDBReader;
import com.aurorain.shardkv.store.WriteBatch;
import io.netty.util.internal.StringUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.RocksIterator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * @author aurorain
 * @version 1.0
 */
@Slf4j
public class ShardKVServer implements KVServerService {

    private ServerState state = new ServerState();

    private static final int SNAPSHOT_LOG_GAP = 3;

    private static final double THRESHOLD = 0.8;

    public VertxHttpServer init(int me, int gid, Map<Integer, RaftService> peers, Persister persister, int maxRaftState) {
        state.setMe(me);
        state.setGid(gid);
        state.setMaxRaftState(maxRaftState);
        state.setChannel(new Channel<>(5));
        Raft raft = new Raft();
        state.setRaft(raft);
        raft.init(peers, me, persister, state.getChannel(), false);
        state.setStore(new RocksDBKV("D:/Papers/graduation/aurorainkv/db" + me));
        state.setCmdResponseChannels(new HashMap<>());
        state.setLastCmdContext(new HashMap<>());
        state.setLastApplied(0);
        state.setLastSnapshot(0);
        ShardConfig shardConfig = new ShardConfig();
        HashMap<Integer, List<Integer>> groups = new HashMap<>();
//        ArrayList<Integer> list = new ArrayList<>();
//        for(int i = 0; i < NShard; ++i) {
//            list.add(i);
//        }
//        groups.put(1, list);
        shardConfig.setGroups(groups);
        state.setShardConfig(shardConfig);
        state.setComeInShards(new HashMap<>());
        state.setToOutShards(new HashMap<>());
        state.setGarbagesBackUp(new HashMap<>());
        state.setGarbages(new HashMap<>());
        HashSet<Integer> avaliableShards = new HashSet<>();
//        for(int i = 0; i < ShardConstant.NShard; ++i) {
//            avaliableShards.add(i);
//        }
        state.setAvailableShards(avaliableShards);

        state.setGarbageFinished(0);
        state.setGarbageConfigNum(0);
        state.setPullShardFinished(0);

        state.setLatches(new Latches());

        // 每次初始化都从持久化对象中恢复数据，只读取 snapshot 而非 raftState
        installSnapshot(persister.readSnapshot());

        new Thread(this::applier).start();
        new Thread(this::snapshoter).start();
        new Thread(this::updateConfigLoop).start();
        new Thread(this::pullShardLoop).start();
        new Thread(this::garbagesCollectLoop).start();
        return new VertxHttpServer(raft);
    }

    @Override
    public CommandResponse requestCommand(CommandRequest request) {

        state.getLock().lock();
        log.info("{} received command request {}", state.getMe(), request);

        Command command = request.getCommand();
        CommandType type = command.getType();
        int clientId = command.getClientId();
        int seqId = command.getSeqId();
        // 检查操作是否可行
        if (!type.equals(CommandType.GET) && isDuplicated(clientId, seqId)) {
            CommandContext context = state.getLastCmdContext().get(clientId);
            state.getLock().unlock();
            return context.getResponse();
        }
        state.getLock().unlock();
//        log.info("111111111111111111111111111111");
        CommandResponse response = new CommandResponse();
        Command cmd = new Command();
        cmd.setType(type);
        cmd.setKey(command.getKey());
        cmd.setValue(command.getValue());
        cmd.setClientId(clientId);
        cmd.setSeqId(seqId);
        // 将命令追加到 raft 之中，随后开启通道等待来自 applier 的消息
        Raft raft = state.getRaft();
        int index = raft.startCmd(cmd);
        int term = raft.getTerm();
        // 检查状态，是否为 leader
        if (index == -1) {
            response.setValue("");
            response.setSuccess(false);
            response.setErr(com.aurorain.shardkv.constant.Message.WRONG_LEADER);
            log.info("wrong leader, id is {}", state.getMe());
            return response;
        }
        log.info("correct leader, id is {}", state.getMe());
        state.getLock().lock();
        // 新建一个通道，供 applier 写入，该函数只创建通道和读取通道的信息
        ShardIndexAndTerm it = new ShardIndexAndTerm(index, term);
        Channel<CommandResponse> channel = new Channel<>(1);
        state.getCmdResponseChannels().put(it, channel);
        state.getLock().unlock();
//        log.info("3333333333333333333333333");
        // 在一定的时间段里反复读取通道里的消息
        LocalDateTime time = LocalDateTime.now().plus(com.aurorain.shardkv.constant.TimeConstant.CMD_TIMEOUT, ChronoUnit.MILLIS);
        while (LocalDateTime.now().isBefore(time)) {
            state.getLock().lock();
            CommandResponse res = null;
//            log.info("没有收到applier");
            if ((res = channel.read()) != null) {
                log.info("{} had applied, {}", state.getMe(), res);
                // 销毁通道
                state.getCmdResponseChannels().remove(it);
                state.getLock().unlock();
                return res;
            }
            state.getLock().unlock();
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GAP_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
//        log.info("44444444444444444444444444444444");
        response.setValue("");
        response.setSuccess(false);
        response.setErr(com.aurorain.shardkv.constant.Message.TIMEOUT);
        state.getCmdResponseChannels().remove(it);
        return response;
    }

    private RocksDBReader requestReader(CommandRequest request) {

        state.getLock().lock();
        log.info("{} received command request {}", state.getMe(), request);

        Command command = request.getCommand();
        CommandType type = command.getType();
        int clientId = command.getClientId();
        int seqId = command.getSeqId();
        state.getLock().unlock();
//        log.info("111111111111111111111111111111");
        Command cmd = new Command();
        cmd.setKey(command.getKey());
        cmd.setValue(command.getValue());
        cmd.setClientId(clientId);
        cmd.setSeqId(seqId);
        cmd.setType(CommandType.SNAP);
        // 将命令追加到 raft 之中，随后开启通道等待来自 applier 的消息
        Raft raft = state.getRaft();
        int index = raft.startCmd(cmd);
        int term = raft.getTerm();
        // 检查状态，是否为 leader
        if (index == -1) {
            return null;
        }
        log.info("correct leader, id is {}", state.getMe());
        state.getLock().lock();
        // 新建一个通道，供 applier 写入，该函数只创建通道和读取通道的信息
        ShardIndexAndTerm it = new ShardIndexAndTerm(index, term);
        Channel<CommandResponse> channel = new Channel<>(1);
        state.getCmdResponseChannels().put(it, channel);
        state.getLock().unlock();
//        log.info("3333333333333333333333333");
        // 在一定的时间段里反复读取通道里的消息
        LocalDateTime time = LocalDateTime.now().plus(com.aurorain.shardkv.constant.TimeConstant.CMD_TIMEOUT, ChronoUnit.MILLIS);
        while (LocalDateTime.now().isBefore(time)) {
            state.getLock().lock();
            CommandResponse res = null;
//            log.info("没有收到applier");
            if ((res = channel.read()) != null) {
                log.info("{} had applied, {}", state.getMe(), res);
                // 销毁通道
                state.getCmdResponseChannels().remove(it);
                state.getLock().unlock();
                return (RocksDBReader) res.getValue();
            }
            state.getLock().unlock();
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GAP_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
//        log.info("44444444444444444444444444444444");
        return null;
    }

    public CommandResponse kvPrewrite(CommandRequest request) {
        if(!state.getRaft().isLeader()) {return new CommandResponse(false, "not leader!");}
        RocksDBReader rocksDBReader = requestReader(request);
        StringBuilder errMsg = new StringBuilder();
        if(rocksDBReader == null) {
            log.info("kvPrewrite:rocksdb iterator is null");
            errMsg.append("rocksdb iterator is null");
        }
        PreWriteRequest req = (PreWriteRequest)request;
        MvccTxn txn = new MvccTxn(req.getStartTs(), rocksDBReader);

        for(RocksDBEntry entry : req.getEntries()) {
            WriteTs writeTs = txn.mostRecentWrite(entry.getKey());
            if(writeTs.getWrite() != null && req.getStartTs() <= writeTs.getTs()) {
                log.info("write conflict");
                errMsg.append("entry write config:").append(entry.getKey()).append(",").append(entry.getValue()).append(":");
                continue;
            }
            Lock lock = txn.getLock(entry.getKey());
            if(lock != null && lock.getTs() != req.getStartTs()) {
                log.info("已经有锁了!");
                errMsg.append("entry has lock:").append(entry.getKey()).append(",").append(entry.getValue()).append(":");
            }
            int kind;
            switch(entry.getType()) {
                case PUT:{
                    kind = WriteConstants.WRITE_KIND_PUT;
                    txn.putValue(entry.getKey(), entry.getValue());
                    break;
                }
                case DELETE:{
                    kind = WriteConstants.WRITE_KIND_DELETE;
                    txn.deleteValue(entry.getKey());
                    break;
                }
                default:{
                    rocksDBReader.close();
                    throw new RuntimeException("invalid oper");
                }
            }
            Lock l = new Lock(req.getPrimary(), req.getStartTs(), req.getLockTtl(), kind);
            txn.putLock(entry.getKey(), l);

        }
        WriteBatch writeBatch = WriteBatch.getWriteBatch(txn.getWrites());
        CommandResponse response = new CommandResponse();
        Command cmd = new Command();
        Command command = request.getCommand();
        cmd.setType(CommandType.WRITEBATCH);
        cmd.setKey(command.getKey());
        cmd.setValue(writeBatch);
        cmd.setClientId(command.getClientId());
        cmd.setSeqId(command.getSeqId());
        // 将命令追加到 raft 之中，随后开启通道等待来自 applier 的消息
        Raft raft = state.getRaft();
        int index = raft.startCmd(cmd);
        int term = raft.getTerm();
        // 检查状态，是否为 leader
        if (index == -1) {
            response.setValue("");
            response.setSuccess(false);
            response.setErr(com.aurorain.shardkv.constant.Message.WRONG_LEADER);
            log.info("wrong leader, id is {}", state.getMe());
            rocksDBReader.close();
            return response;
        }
        log.info("correct leader, id is {}", state.getMe());
        state.getLock().lock();
        // 新建一个通道，供 applier 写入，该函数只创建通道和读取通道的信息
        ShardIndexAndTerm it = new ShardIndexAndTerm(index, term);
        Channel<CommandResponse> channel = new Channel<>(1);
        state.getCmdResponseChannels().put(it, channel);
        state.getLock().unlock();
//        log.info("3333333333333333333333333");
        // 在一定的时间段里反复读取通道里的消息
        LocalDateTime time = LocalDateTime.now().plus(com.aurorain.shardkv.constant.TimeConstant.CMD_TIMEOUT, ChronoUnit.MILLIS);
        while (LocalDateTime.now().isBefore(time)) {
            state.getLock().lock();
            CommandResponse res = null;
//            log.info("没有收到applier");
            if ((res = channel.read()) != null) {
                log.info("{} had applied, {}", state.getMe(), res);
                // 销毁通道
                state.getCmdResponseChannels().remove(it);
                state.getLock().unlock();
                rocksDBReader.close();
                return res;
            }
            state.getLock().unlock();
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GAP_TIME);
            } catch (InterruptedException e) {
                rocksDBReader.close();
                throw new RuntimeException(e);
            }
        }

        String errMessage = errMsg.toString();
        rocksDBReader.close();
        return new CommandResponse(false, errMessage);

    }

    public CommandResponse kvCommit(CommandRequest request) {
        if(!state.getRaft().isLeader()) {return new CommandResponse(false, "wrong leader!");}
        RocksDBReader rocksDBReader = requestReader(request);
        CommitRequest req = (CommitRequest) request;
        MvccTxn mvccTxn = new MvccTxn(req.getStartTs(), rocksDBReader);
        StringBuilder errMsg = new StringBuilder();
        try {
            state.getLatches().waitForLatches(req.getKeys());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        for(String key : req.getKeys()) {
            Lock lock = mvccTxn.getLock(key);
            if(lock == null || lock.getTs() != req.getStartTs()) {
                state.getLatches().releaseLatches(req.getKeys());
                rocksDBReader.close();
                return new CommandResponse(false, "key entry get lock error" + key + ":");
            }

            mvccTxn.putWrite(key, req.getCommitTs(), new Write(req.getStartTs(), lock.getKind()));
            mvccTxn.deleteLock(key);
        }
        WriteBatch writeBatch = WriteBatch.getWriteBatch(mvccTxn.getWrites());
        CommandResponse response = new CommandResponse();
        Command cmd = new Command();
        Command command = request.getCommand();
        cmd.setType(CommandType.WRITEBATCH);
        cmd.setKey(command.getKey());
        cmd.setValue(writeBatch);
        cmd.setClientId(command.getClientId());
        cmd.setSeqId(command.getSeqId());
        // 将命令追加到 raft 之中，随后开启通道等待来自 applier 的消息
        Raft raft = state.getRaft();
        int index = raft.startCmd(cmd);
        int term = raft.getTerm();
        // 检查状态，是否为 leader
        if (index == -1) {
            response.setValue("");
            response.setSuccess(false);
            response.setErr(com.aurorain.shardkv.constant.Message.WRONG_LEADER);
            log.info("wrong leader, id is {}", state.getMe());
            state.getLatches().releaseLatches(req.getKeys());
            rocksDBReader.close();
            return response;
        }
        log.info("correct leader, id is {}", state.getMe());
        state.getLock().lock();
        // 新建一个通道，供 applier 写入，该函数只创建通道和读取通道的信息
        ShardIndexAndTerm it = new ShardIndexAndTerm(index, term);
        Channel<CommandResponse> channel = new Channel<>(1);
        state.getCmdResponseChannels().put(it, channel);
        state.getLock().unlock();
//        log.info("3333333333333333333333333");
        // 在一定的时间段里反复读取通道里的消息
        LocalDateTime time = LocalDateTime.now().plus(com.aurorain.shardkv.constant.TimeConstant.CMD_TIMEOUT, ChronoUnit.MILLIS);
        while (LocalDateTime.now().isBefore(time)) {
            state.getLock().lock();
            CommandResponse res = null;
//            log.info("没有收到applier");
            if ((res = channel.read()) != null) {
                log.info("{} had applied, {}", state.getMe(), res);
                // 销毁通道
                state.getCmdResponseChannels().remove(it);
                state.getLock().unlock();
                state.getLatches().releaseLatches(req.getKeys());
                rocksDBReader.close();
                return res;
            }
            state.getLock().unlock();
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GAP_TIME);
            } catch (InterruptedException e) {
                state.getLatches().releaseLatches(req.getKeys());
                rocksDBReader.close();
                throw new RuntimeException(e);
            }
        }

        String errMessage = errMsg.toString();
        state.getLatches().releaseLatches(req.getKeys());
        rocksDBReader.close();
        return new CommandResponse(false, errMessage);

    }

    public CommandResponse kvGet(CommandRequest request) {
        RocksDBReader rocksDBReader = requestReader(request);
        TCommandRequest req = (TCommandRequest) request;
        MvccTxn txn = new MvccTxn(req.getTs(), rocksDBReader);
        Lock lock = txn.getLock(req.getCommand().getKey());
        if(lock != null && req.getTs() >= lock.getTs()) {
            rocksDBReader.close();
            return new CommandResponse(false, "kvGet:lock entry error for key:" + req.getCommand().getKey());
        }
        String value = txn.getValue(req.getCommand().getKey());
        CommandResponse response = new CommandResponse();
        response.setSuccess(true);
        response.setValue(value);
        rocksDBReader.close();
        return response;
    }

    public CommandResponse kvScan(CommandRequest request) {
        RocksDBReader rocksDBReader = requestReader(request);
        ScanRequest req = (ScanRequest) request;
        MvccTxn txn = new MvccTxn(req.getTs(), rocksDBReader);
        Scanner scanner = new Scanner(req.getStartKey(), txn);
        List<KVPair> kvPairs = new ArrayList<>();
        StringBuilder errMsg = new StringBuilder();
        for(int i = 0;i < req.getLimit(); ++i) {
            KVPair pair = scanner.next();
            if(StringUtil.isNullOrEmpty(pair.getKey())) {
                break;
            }
            Lock lock = txn.getLock(pair.getKey());
            if(lock == null && req.getTs() >= lock.getTs()) {
                errMsg.append("lock for key:" + pair.getKey());
                continue;
            }
            if(!StringUtil.isNullOrEmpty(pair.getValue())) {
                kvPairs.add(pair);
            }
        }
        CommandResponse response = new CommandResponse();
        response.setSuccess(true);
        response.setErr(errMsg.toString());
        response.setValue(kvPairs);
        rocksDBReader.close();
        return response;
    }

    public CommandResponse kvCheckTxnStatus(CommandRequest request) {
        if(!state.getRaft().isLeader()) {return new CommandResponse(false, "not leader");}
        RocksDBReader rocksDBReader = requestReader(request);
        CheckTxnStatusRequest req = (CheckTxnStatusRequest) request;
        MvccTxn txn = new MvccTxn(req.getLockTs(), rocksDBReader);
        WriteTs writeTs = txn.currentWrite(req.getPrimaryKey());
        Write write = writeTs.getWrite();
        CheckTxnStatusResponse response = new CheckTxnStatusResponse();
        if(write != null) {
            if(write.getWriteKind() != WriteConstants.WRITE_KIND_ROLLBACK) {
                response.setCommitTs(writeTs.getTs());
            }
            rocksDBReader.close();
            return response;
        }
        Lock lock = txn.getLock(req.getPrimaryKey());
        if(lock == null) {
            txn.putWrite(req.getPrimaryKey(), req.getLockTs(), new Write(req.getLockTs(), WriteConstants.WRITE_KIND_ROLLBACK));
            rocksDBReader.close();
            return response;
        }
        if(TransactionUtil.physicalTime(lock.getTs()) + lock.getTtl() <= TransactionUtil.physicalTime(req.getCommitTs())) {
            txn.deleteLock(req.getPrimaryKey());
            txn.deleteValue(req.getPrimaryKey());
            txn.putWrite(req.getPrimaryKey(), req.getLockTs(), new Write(req.getLockTs(), WriteConstants.WRITE_KIND_ROLLBACK));
            WriteBatch writeBatch = WriteBatch.getWriteBatch(txn.getWrites());
            Command cmd = new Command();
            Command command = request.getCommand();
            cmd.setType(CommandType.WRITEBATCH);
            cmd.setKey(command.getKey());
            cmd.setValue(writeBatch);
            cmd.setClientId(command.getClientId());
            cmd.setSeqId(command.getSeqId());
            // 将命令追加到 raft 之中，随后开启通道等待来自 applier 的消息
            Raft raft = state.getRaft();
            int index = raft.startCmd(cmd);
            int term = raft.getTerm();
            // 检查状态，是否为 leader
            if (index == -1) {
                response.setValue("");
                response.setSuccess(false);
                response.setErr(com.aurorain.shardkv.constant.Message.WRONG_LEADER);
                log.info("wrong leader, id is {}", state.getMe());
                rocksDBReader.close();
                return response;
            }
            log.info("correct leader, id is {}", state.getMe());
            state.getLock().lock();
            // 新建一个通道，供 applier 写入，该函数只创建通道和读取通道的信息
            ShardIndexAndTerm it = new ShardIndexAndTerm(index, term);
            Channel<CommandResponse> channel = new Channel<>(1);
            state.getCmdResponseChannels().put(it, channel);
            state.getLock().unlock();
//        log.info("3333333333333333333333333");
            // 在一定的时间段里反复读取通道里的消息
            LocalDateTime time = LocalDateTime.now().plus(com.aurorain.shardkv.constant.TimeConstant.CMD_TIMEOUT, ChronoUnit.MILLIS);
            while (LocalDateTime.now().isBefore(time)) {
                state.getLock().lock();
                CommandResponse res = null;
//            log.info("没有收到applier");
                if ((res = channel.read()) != null) {
                    log.info("{} had applied, {}", state.getMe(), res);
                    // 销毁通道
                    state.getCmdResponseChannels().remove(it);
                    state.getLock().unlock();
                    response.setSuccess(true);
                    response.setValue(res.getValue());
                    response.setAction(ActionConstants.ACTION_TTLEXPIREROLLBACK);
                    rocksDBReader.close();
                    return response;
                }
                state.getLock().unlock();
                try {
                    Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GAP_TIME);
                } catch (InterruptedException e) {
                    rocksDBReader.close();
                    throw new RuntimeException(e);
                }
            }

        }
        response.setSuccess(false);
        return response;

    }

    public CommandResponse KvBatchRollback(CommandRequest request) {
        if(!state.getRaft().isLeader()) {return new CommandResponse(false, "error msg!");}
        RocksDBReader rocksDBReader = requestReader(request);
        BatchRollbackRequest req = (BatchRollbackRequest) request;
        MvccTxn txn = new MvccTxn(req.getStartTs(), rocksDBReader);
        try {
            state.getLatches().waitForLatches(req.getKeys());
        } catch (InterruptedException e) {
            rocksDBReader.close();
            throw new RuntimeException(e);
        }
        for(String key : req.getKeys()) {
            WriteTs writeTs = txn.currentWrite(key);
            Write write = writeTs.getWrite();
            if(write != null) {
                if(write.getWriteKind() == WriteConstants.WRITE_KIND_ROLLBACK) {
                    continue;
                } else {
                    rocksDBReader.close();
                    state.getLatches().releaseLatches(req.getKeys());
                    return new CommandResponse(false, "key error!");
                }
            }
            Lock lock = txn.getLock(key);
            if(lock == null || lock.getTs() != req.getStartTs()) {
                txn.putWrite(key, req.getStartTs(), new Write(req.getStartTs(), WriteConstants.WRITE_KIND_ROLLBACK));
                continue;
            }
            txn.deleteLock(key);
            txn.deleteValue(key);
            txn.putWrite(key, req.getStartTs(), new Write(req.getStartTs(), WriteConstants.WRITE_KIND_ROLLBACK));
        }
        WriteBatch writeBatch = WriteBatch.getWriteBatch(txn.getWrites());
        CommandResponse response = new CommandResponse();
        Command cmd = new Command();
        Command command = request.getCommand();
        cmd.setType(CommandType.WRITEBATCH);
        cmd.setKey(command.getKey());
        cmd.setValue(writeBatch);
        cmd.setClientId(command.getClientId());
        cmd.setSeqId(command.getSeqId());
        // 将命令追加到 raft 之中，随后开启通道等待来自 applier 的消息
        Raft raft = state.getRaft();
        int index = raft.startCmd(cmd);
        int term = raft.getTerm();
        // 检查状态，是否为 leader
        if (index == -1) {
            response.setValue("");
            response.setSuccess(false);
            response.setErr(com.aurorain.shardkv.constant.Message.WRONG_LEADER);
            log.info("wrong leader, id is {}", state.getMe());
            rocksDBReader.close();
            state.getLatches().releaseLatches(req.getKeys());
            return response;
        }
        log.info("correct leader, id is {}", state.getMe());
        state.getLock().lock();
        // 新建一个通道，供 applier 写入，该函数只创建通道和读取通道的信息
        ShardIndexAndTerm it = new ShardIndexAndTerm(index, term);
        Channel<CommandResponse> channel = new Channel<>(1);
        state.getCmdResponseChannels().put(it, channel);
        state.getLock().unlock();
//        log.info("3333333333333333333333333");
        // 在一定的时间段里反复读取通道里的消息
        LocalDateTime time = LocalDateTime.now().plus(com.aurorain.shardkv.constant.TimeConstant.CMD_TIMEOUT, ChronoUnit.MILLIS);
        while (LocalDateTime.now().isBefore(time)) {
            state.getLock().lock();
            CommandResponse res = null;
//            log.info("没有收到applier");
            if ((res = channel.read()) != null) {
                log.info("{} had applied, {}", state.getMe(), res);
                // 销毁通道
                state.getCmdResponseChannels().remove(it);
                state.getLock().unlock();
                response.setSuccess(true);
                response.setValue(res.getValue());
                rocksDBReader.close();
                state.getLatches().releaseLatches(req.getKeys());
                return response;
            }
            state.getLock().unlock();
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GAP_TIME);
            } catch (InterruptedException e) {
                rocksDBReader.close();
                state.getLatches().releaseLatches(req.getKeys());
                throw new RuntimeException(e);
            }
        }
        rocksDBReader.close();
        state.getLatches().releaseLatches(req.getKeys());
        return new CommandResponse(false, "");

    }

    public CommandResponse kvResolveLock(CommandRequest request) {
        RocksDBReader rocksDBReader = requestReader(request);
        ResolveLockRequest req = (ResolveLockRequest) request;
        RocksIterator iterator = rocksDBReader.iterCf(CFConstants.CfLock);
        List<String> keys = new ArrayList<>();
        for(;iterator.isValid();iterator.next()) {
            String value = new String(iterator.value());
            Lock lock = Lock.parse(value);
            if(lock.getTs() == req.getStartTs()) {
                keys.add(new String(iterator.key()));
            }
        }
        iterator.close();
        rocksDBReader.close();
        if(keys.isEmpty()) {
            return new CommandResponse(true, "keys is empty");
        }
        if(req.getCommitTs() == 0) {
            return new CommandResponse(true,  "commitTs == 0");
        } else {
            return new CommandResponse(true, "");
        }
    }

    /**
     * 代替了 RaftApplication 的 applierSnap，接收来自 leader 提交的日志
     * handler 向 raft 中追加命令，raft 只负责记录命令序列
     * 在保证一致性的情况下 raft 会向 applier 提交命令，实际调用 kv 存储并执行命令是在 applier 中完成的
     */
    public void applier() {
        Channel<ApplyMsg> channel = state.getChannel();
        while (!killed()) {
            ApplyMsg applyMsg = null;
            if ((applyMsg = channel.read()) != null) {
//                log.info("收到applyMsg");
                if (applyMsg.isCommandValid()) {
                    state.getLock().lock();

                    // 检查消息是否过时
                    if (applyMsg.getCommandIndex() < state.getLastApplied()) {
                        log.info("{} was too old", applyMsg);
                        state.getLock().unlock();
                        continue;
                    }
                    state.setLastApplied(applyMsg.getCommandIndex());

                    // 判断命令类型并记录
                    Command command = (Command) applyMsg.getCommand();
                    CommandResponse response = new CommandResponse();
                    int seqId = command.getSeqId();
                    int clientId = command.getClientId();
                    if(command.getType().equals(CommandType.UPDATECONFIG)) {
                        ShardConfig config = ShardConfig.fromJsonString((String)command.getValue());
                        updateComeInAndOutShards(config);
                    } else if(command.getType().equals(CommandType.UPDATEDATEBASE)) {
                        MigrateReply migrateReply = MigrateReply.fromString((String)command.getValue());
                        updateDatabaseWithBaseWithMigrateReply(migrateReply);
                    } else if (command.getType() == CommandType.GC){
                        GarbagesCollectArgs args = GarbagesCollectArgs.fromString((String)command.getValue());
                        clearToOutData(args.getConfigNum(), args.getShard());
                        response.setValue(null);
                        response.setSuccess(true);
                        // 更新命令上下文
                        CommandContext context = new CommandContext(seqId, response);
                        state.getLastCmdContext().put(clientId, context);
                    } else if (command.getType() == CommandType.SNAP){
                        RocksDBReader rocksDBReader = state.getStore().openReader();
                        response.setValue(rocksDBReader);
                        response.setSuccess(true);
//                        CommandContext context = new CommandContext(seqId, response);
//                        state.getLastCmdContext().put(clientId, context);
                    } else if(command.getType() == CommandType.WRITEBATCH) {
                        WriteBatch writeBatch = (WriteBatch)command.getValue();
                        try {
                            state.getStore().opt(writeBatch);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        response.setSuccess(true);
                    } else if (command.getType() != CommandType.GET && isDuplicated(clientId, seqId)) {
                        // 如果存在对应的命令记录则获取 response
                        CommandContext context = state.getLastCmdContext().get(clientId);
                        response = context.getResponse();
                    } else {
                        log.info("收到的command的type为{}，key为{}，seqId为{}，clientId为{}", command.getType(), command.getKey(), command.getSeqId(), command.getClientId());
                        int shard = state.getClient().key2shard(command.getKey());
                        if(!state.getAvailableShards().contains(shard)) {
                            log.info("no avaliable shard:{}", shard);
                            response.setErr("no avaliable shard " + shard);
                            response.setSuccess(false);

                        } else {
                            // 如果不存在记录则调用 kv 的 opt 获取并初始化 response
                            String value = state.getStore().opt(command);
                            response.setValue(value);
                            response.setSuccess(true);
                            log.info("response is : {}", response.getValue());
                            // 更新命令上下文
                            CommandContext context = new CommandContext(seqId, response);
                            state.getLastCmdContext().put(clientId, context);
                        }
                    }

                    // 检查状态
                    if (!state.getRaft().isLeader() || applyMsg.getCommandTerm() != state.getRaft().getTerm()) {
                        state.getLock().unlock();
                        continue;
                    }

                    // 通过通道把 response 返回给 handler
                    Channel<CommandResponse> responseChannel = state.getCmdResponseChannels().get(new ShardIndexAndTerm(applyMsg.getCommandIndex(), applyMsg.getCommandTerm()));
                    if (responseChannel != null && (!responseChannel.write(response))) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    state.getLock().unlock();
                } else if (applyMsg.isSnapShotValid()) {
                    state.getLock().lock();
                    // follower 会在每次生成快照后进入这个分支一次，raft 和 kv server 同时完成快照的安装
                    if (state.getRaft().condInstallSnapshot(applyMsg.getSnapShotTerm(), applyMsg.getSnapShotIndex(), applyMsg.getSnapShot())) {
                        installSnapshot(applyMsg.getSnapShot());
                        state.setLastApplied(applyMsg. getSnapShotIndex());
                    }
                    state.getLock().unlock();
                }
            } else {
                try {
                    Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.APPLIER_TIME);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void updateConfigLoop() {
        while(!killed()) {
            state.getLock().lock();
            if(!state.getRaft().isLeader()) {
                state.getLock().unlock();
                try {
                    Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.PULLCONFIG_GAP_TIME);
                } catch (Exception e) {

                }
                continue;
            }
            int nextConfigNum = state.getShardConfig().getNum() + 1;
            state.getLock().unlock();
            ShardConfig config = state.getShardClient().query(nextConfigNum);
            if(!state.getShardConfig().equals(config)) {
                Client client = state.getClient();
                Command command = client.getCommand("random", config.toJsonString(), CommandType.UPDATECONFIG);
                Raft raft = state.getRaft();
                raft.startCmd(command);
            }
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.PULLCONFIG_GAP_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void pullShardLoop() {
        while(!killed()) {
            state.getLock().lock();
            if(!state.getRaft().isLeader()) {
                state.getLock().unlock();
                try {
                    Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.PULLSHARDLOOP_GAP_TIME);
                } catch (Exception e) {

                }
                continue;
            }
            // 遍历所有的comeInShards里面的shard发RPC
            int tmpShardsSize = state.getComeInShards().size();
            state.setPullShardFinished(0);
            state.setPullShardIter(state.getComeInShards().entrySet().iterator());
            state.getLock().unlock();
            Thread[] threads = new Thread[tmpShardsSize];
            for(int i = 0; i < tmpShardsSize; ++i) {
                threads[i] = new Thread(this::doPullShard);
                threads[i].start();
            }
            while(state.getPullShardFinished() != tmpShardsSize) {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {

                }
            }
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.PULLSHARDLOOP_GAP_TIME);
            } catch (Exception e) {

            }
        }
    }

    private void garbagesCollectLoop() {
        while(!killed()) {
            state.getLock().lock();
            if(!state.getRaft().isLeader()) {
                state.getLock().unlock();
                try {
                    Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GARBAGESLOOP_GAP_TIME);
                } catch (Exception e) {

                }
                continue;
            }
            state.setGarbagesBackUp(new HashMap<>(state.getGarbages()));
            state.getLock().unlock();
            for(Map.Entry<Integer, HashSet<Integer>> entry : state.getGarbagesBackUp().entrySet()) {
                state.getLock().lock();
                int tmpGarbagesSize = entry.getValue().size();
                Iterator<Integer> iterator = entry.getValue().iterator();
                state.setGarbageIter(iterator);
                state.setGarbageConfigNum(entry.getKey());
                state.setGarbageFinished(0);
                state.getLock().unlock();

                Thread[] threads = new Thread[tmpGarbagesSize];
                for(int i = 0; i < tmpGarbagesSize; ++i) {
                    threads[i] = new Thread(this::doGarbage);
                    threads[i].start();
                }

                while(state.getGarbageFinished() != tmpGarbagesSize) {
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e) {

                    }
                }
            }
            state.getGarbagesBackUp().clear();
            try {
                Thread.sleep(com.aurorain.shardkv.constant.TimeConstant.GARBAGESLOOP_GAP_TIME);
            } catch (Exception e) {

            }
        }
    }

    private void doPullShard() {
        MigrateArgs args = new MigrateArgs();

        state.getLock().lock();
        try {
            Map.Entry<Integer, Integer> tmpData = state.getPullShardIter().next();
            args.setShard(tmpData.getKey());
            args.setConfigNum(tmpData.getValue());
        } finally {
            state.getLock().unlock();
        }

        ShardConfig config = state.getShardClient().query(args.getConfigNum());
        int gid = config.getShards()[args.getShard()];
        boolean isOk = false;

        // 只有一个是leader
        for(Integer sid : config.getGroups().get(gid)) {
            if(isOk) break;
            CommandResponse<MigrateReply> response = state.getClient().rpcCall(args.toString(), "", CommandType.SHARDMIGRATE, state.getClient().getKvServices(gid).get(sid), CommandType.SHARDMIGRATE.getDescription());
            MigrateReply reply = response.getValue();
            if(!reply.isLeader()) continue;
            if(response.isSuccess()) {
                isOk = true;
                Client client = state.getClient();
                Command command = client.getCommand("random", reply.toString(), CommandType.UPDATEDATEBASE);
                Raft raft = state.getRaft();
                raft.startCmd(command);
            }
        }
        state.getLock().lock();
        state.setPullShardFinished(state.getPullShardFinished()+1);
        state.getLock().unlock();

    }

    public void updateComeInAndOutShards(ShardConfig config) {
        state.getLock().lock();
        // 防止重复调用
        if(config.getNum() <= state.getShardConfig().getNum()) {
            state.getLock().unlock();
            return;
        }
        // [0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
        ShardConfig oldConfig = state.getShardConfig();
        // null
        HashSet<Integer> tmpToOutShardMap = new HashSet<>(state.getAvailableShards());
        state.setShardConfig(config);
        state.getAvailableShards().clear();
        for(int i = 0; i < config.getShards().length; ++i) {
            if(config.getShards()[i] != state.getGid()) {
                continue;
            }
            if(tmpToOutShardMap.contains(i) || oldConfig.getNum() == 0) {
                tmpToOutShardMap.remove(i);
                state.getAvailableShards().add(i);
            } else {
                state.getComeInShards().put(i, oldConfig.getNum());
            }
        }
        if(tmpToOutShardMap.size() > 0) {
           for(Integer shard : tmpToOutShardMap) {
               Map<String, String> tmpDatabase = new HashMap<>();
               Iterator<Map.Entry<String, String>> iterator = state.getStore().iterator();
               while(iterator.hasNext()) {
                   Map.Entry<String, String> entry = iterator.next();
                   if(state.getClient().key2shard(entry.getKey()) == shard) {
                       tmpDatabase.put(entry.getKey(), entry.getValue());
                       state.getStore().delete(entry.getKey());
                   }
               }
               if(!state.getToOutShards().containsKey(oldConfig.getNum())) {
                   state.getToOutShards().put(oldConfig.getNum(), new HashMap<>());
               }
               if(!state.getToOutShards().get(oldConfig.getNum()).containsKey(shard)) {
                   state.getToOutShards().get(oldConfig.getNum()).put(shard, new HashMap<>());
               }
               state.getToOutShards().get(oldConfig.getNum()).get(shard).putAll(tmpDatabase);
           }
        }
        log.info(Thread.currentThread().getId() + ":" + "new config : {}, availableShards : {}", config, state.getAvailableShards());
        state.getLock().unlock();

    }

    public CommandResponse shardMigration(CommandRequest request) {
        Command command = request.getCommand();
        MigrateArgs args = MigrateArgs.fromString(command.getKey());
        CommandResponse response = new CommandResponse<>();
        response.setSuccess(false);
        MigrateReply migrateReply = new MigrateReply();
        response.setValue(migrateReply);
        migrateReply.setLeader(false);
        migrateReply.setShard(args.getShard());
        migrateReply.setConfigNum(args.getConfigNum());
        migrateReply.setDatabase(new HashMap<>());
        migrateReply.setClientReqId(new HashMap<>());

        boolean leader = state.getRaft().isLeader();
        if(!leader) return response;
        migrateReply.setLeader(true);
        state.getLock().lock();
        if(migrateReply.getConfigNum() >= state.getShardConfig().getNum()) {
            state.getLock().unlock();
            return response;
        }
        for(Map.Entry<String, String> entry : state.getToOutShards().get(args.getConfigNum()).get(args.getShard()).entrySet()) {
            migrateReply.getDatabase().put(entry.getKey(), entry.getValue());
        }
        for(Map.Entry<Integer, CommandContext> entry : state.getLastCmdContext().entrySet()) {
            migrateReply.getClientReqId().put(entry.getKey(), entry.getValue());
        }
        state.getLock().unlock();
        response.setSuccess(true);
        return response;
    }

    private void updateDatabaseWithBaseWithMigrateReply(MigrateReply reply) {
        state.getLock().lock();

        if(reply.getConfigNum() != state.getShardConfig().getNum() - 1) {
            state.getLock().unlock();
            return;
        }

        state.getComeInShards().remove(reply.getShard());
        if(!state.getAvailableShards().contains(reply.getShard())) {
            for(Map.Entry<String, String> entry : reply.getDatabase().entrySet()) {
                state.getStore().put(entry.getKey(), entry.getValue());
            }
            for(Map.Entry<Integer,CommandContext> entry : reply.getClientReqId().entrySet()) {
                if(entry.getValue().getSeqId() > state.getLastCmdContext().get(entry.getKey()).getSeqId()) {
                    state.getLastCmdContext().put(entry.getKey(), entry.getValue());
                }
            }
            state.getAvailableShards().add(reply.getShard());
            // TODO ?
            if(!state.getGarbages().containsKey(reply.getConfigNum())) {
                state.getGarbages().put(reply.getConfigNum(), new HashSet<>());
            }
            state.getGarbages().get(reply.getConfigNum()).add(reply.getShard());
        }
        state.getLock().unlock();

    }

    private void doGarbage() {
        GarbagesCollectArgs args = new GarbagesCollectArgs();
        state.getLock().lock();
        Iterator<Integer> iterator = state.getGarbageIter();
        args.setShard(iterator.next());
        args.setConfigNum(state.getGarbageConfigNum());
        state.getLock().unlock();
        ShardClient shardClient = state.getShardClient();
        ShardConfig config = shardClient.query(args.getConfigNum());
        int gid = config.getShards()[args.getShard()];
        boolean isOk = false;
        for(Integer sid : config.getGroups().get(gid)) {
            if(isOk) break;
            CommandResponse<GarbagesCollectReply> response = state.getClient().rpcCall("random", args.toString(), CommandType.GARBAGESCOLLECT, state.getClient().getKvServices(gid).get(sid), CommandType.GARBAGESCOLLECT.getDescription());
            GarbagesCollectReply reply = response.getValue();
            if(!reply.isLeader() || !reply.isOk()) continue;
            isOk = true;
            state.getLock().lock();
            state.getGarbages().get(state.getGarbageConfigNum()).remove(args.getShard());
            if(state.getGarbages().get(state.getGarbageConfigNum()).isEmpty()) {
                state.getGarbages().remove(state.getGarbageConfigNum());
            }
            state.getLock().unlock();
        }
        state.getLock().lock();
        state.setGarbageFinished(state.getGarbageFinished()+1);
        state.getLock().unlock();
    }

    public CommandResponse garbagesCollect(CommandRequest request) {
        Command command1 = request.getCommand();
        GarbagesCollectArgs args = GarbagesCollectArgs.fromString(command1.getKey());
        CommandResponse response = new CommandResponse<>();
        GarbagesCollectReply reply = new GarbagesCollectReply();
        response.setSuccess(false);
        response.setValue(reply);
        boolean leader = state.getRaft().isLeader();
        if(!leader) {
            reply.setLeader(false);
            reply.setOk(false);
            return response;
        }
        reply.setLeader(true);
        reply.setOk(false);
        state.getLock().lock();
        if(!state.getToOutShards().containsKey(args.getConfigNum())) {
            state.getLock().unlock();
            return response;
        }
        if(!state.getToOutShards().get(args.getConfigNum()).containsKey(args.getShard())) {
            state.getLock().unlock();
            return response;
        }
        state.getLock().unlock();
        response.setSuccess(true);
        reply.setOk(true);
        Client client = state.getClient();
        Command command = client.getCommand("random", args.toString(), CommandType.GC);
        Raft raft = state.getRaft();
        int index = raft.startCmd(command);
        // TODO 是否需要完善

        return response;
    }

    private void clearToOutData(int cfgNum, int shard) {
        if(state.getToOutShards().containsKey(cfgNum)) {
            state.getToOutShards().get(cfgNum).remove(shard);
            if(state.getToOutShards().get(cfgNum).isEmpty()) {
                state.getToOutShards().remove(cfgNum);
            }
        }
    }

    /**
     * kv server 初始化时开启该线程，检查是否需要裁切日志生成快照
     */
    public void snapshoter() {
        while (!killed()) {
            state.getLock().lock();
            if (isNeedSnapshot()) {
                doSnapshot(state.getLastApplied());
                state.setLastSnapshot(state.getLastApplied());
            }
            state.getLock().unlock();
            try {
                Thread.sleep(TimeConstant.SNAPSHOT_GAP_TIME);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }


//    private HashMap<String, Integer> str2Port;
//
//    private void initStr2PortMap(ServiceMetaInfo[] serviceMetaInfos) {
//        str2Port = new HashMap<>();
//        for(ServiceMetaInfo serviceMetaInfo : serviceMetaInfos) {
//            str2Port.put(serviceMetaInfo.getServiceName(), serviceMetaInfo.getServicePort());
//        }
//    }

    /**
     * 生成快照，由 snapshoter 调用，实质是生成数据后调用 raft 的 snapshot
     *
     * @param commandIndex
     */
    public void doSnapshot(int commandIndex) {
        log.info("kv server {} do snapshot", state.getMe());
        final Serializer serializer = SerializerFactory.getInstance(SerializerKeys.KRYO);
        ServerStatePersist statePersist = new ServerStatePersist(state.getMe());
        BeanUtil.copyProperties(state, statePersist);
        byte[] snapshot;
        try {
            snapshot = serializer.serialize(statePersist);
            if (ArrayUtil.isEmpty(snapshot)) {
                throw new RuntimeException("snapshot failure");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        state.getRaft().snapshot(commandIndex, snapshot);
    }

    /**
     * 安装快照，和 raft 中的 condInstallSnapshot 差不多
     *
     * @param snapshot
     */
    public void installSnapshot(byte[] snapshot) {
        if (ArrayUtil.isEmpty(snapshot)) {
            return;
        }

        log.info("kv server {} install snapshot", state.getMe());
        final Serializer serializer = SerializerFactory.getInstance(SerializerKeys.KRYO);
        try {
            ServerStatePersist deserialize = serializer.deserialize(snapshot, ServerStatePersist.class);
            BeanUtil.copyProperties(deserialize, state);
        } catch (IOException e) {
            throw new RuntimeException("install snapshot failure");
        }
    }

    /**
     * 检查是否需要生成快照（一般是 leader 调用）
     * 1.是否开启 snapshot
     * 2.raft state（带有日志会逐渐变大）是否超过预期大小
     * 3.lastApplied 和 lastSnapshot 的间隔是否大于预期值
     *
     * @return
     */
    public boolean isNeedSnapshot() {
        if (state.getMaxRaftState() != -1
                && state.getRaft().getRaftPersistSize() > THRESHOLD * state.getMaxRaftState()
                && state.getLastApplied() > state.getLastSnapshot() + SNAPSHOT_LOG_GAP) {
            return true;
        }
        return false;
    }

    /**
     * 在完成 put 或 append 操作前要通过这个检查
     * 即要求对应的命令为空或者追加位置大于最后一个位置
     *
     * @param clientId
     * @param seqId
     * @return
     */
    public boolean isDuplicated(int clientId, int seqId) {
        CommandContext context = state.getLastCmdContext().get(clientId);
        return context != null && context.getSeqId() >= seqId;
    }

    public boolean isLeader() {
        return state.getRaft().isLeader();
    }

    public void kill() {
        state.getDead().set(1);
        state.getRaft().kill();
    }

    public boolean killed() {
        return state.getDead().get() == 1;
    }

    public ServerState getState() {
        return state;
    }

}
