package raft;

import proto.pkg.eraftpb.Eraftpb;

import java.sql.Array;
import java.util.*;

enum StateType {
    Follower,Candidate,Leader
}

class Progress {
    // 下一次要发的log index
    long match;
    // 已经发出去并且收到回应的log index
    long next;
}

public class Raft {
    // Raft ID标识
    private long id;
    // 任期号
    private long term;
    // 投票ID
    private long vote;
    // raft日志类
    RaftLog raftLog;
    // raft节点进度副本
    private Map<Integer, Progress> prs;
    // 当前raft节点状态
    private StateType state;
    // 领导者id
    private long leaderId;
    // 投票记录
    private Map<Integer, Boolean> votes;
    // 要发送的msgs
    private List<Eraftpb.Message> msgs;
    // 心跳超时时间，固定值
    private long heartbeatTimeOut;
    // 选举超时时间，基本固定值
    private long electionTimeOut;
    // 选举超时时间，基本固定值 + 一个随机值
    private long randomElectionTimeOut;
    // 不能单纯的使用java的定时任务，比较难实现时间重置，对于选举和心跳都使用tick() -> elapsed++
    private long heartbeatElapsed;
    private long electionElapsed;

    public Raft(RaftConfig config) {
        this.id = config.id;
        this.prs = new HashMap<>();
        this.votes = new HashMap<>();
        this.heartbeatTimeOut = config.heartbeatTick;
        this.electionTimeOut = config.electionTick;
        this.msgs = new ArrayList<>();

        // 其他初始化



    }

    private void becomeFollower(long term, long lead) {
        this.state = StateType.Follower;
        this.leaderId = lead;
        this.term = term;
        this.vote = -1;
    }

    /**
     * 成为候选者，主要是投票初始化
     */
    private void becomeCandidate() {
        this.state = StateType.Candidate;
        this.leaderId = -1;
        this.term++;
        this.vote = id;
        this.votes = new HashMap<>();
        votes.put((int) id, true);
    }

    private void becomeLeader() {
        this.state = StateType.Leader;
        this.leaderId = id;

        // 初始化每个peer的Append进度默认为Leader的lastIndex的下一个
        long lastIndex = raftLog.lastIndex();
        for(Integer peerId : prs.keySet()) {
            if(peerId == (int)id) {
                prs.get(peerId).next = lastIndex + 2;
                prs.get(peerId).match = lastIndex + 1;
            } else {
                prs.get(peerId).next = lastIndex + 1;
            }
        }
        // 心跳计时重置为0
        heartbeatElapsed = 0;

        // 初始化进行一次Append广播
        bcastAppend();

    }
    /**
     * 发送心跳RPC
     * @param to
     */
    private void sendHeartbeat(long to) {
        msgs.add(Eraftpb.Message.newBuilder()
                    .setMsgType(Eraftpb.MessageType.MsgHeartbeat)
                    .setFrom(this.id)
                    .setTo(to)
                    .setTerm(this.term)
                .build());
    }

    /**
     * 发送心跳响应
     * @param to
     * @param reject
     */
    private void sendHeartbeatResponse(long to, boolean reject) {
        msgs.add(Eraftpb.Message.newBuilder()
                        .setMsgType(Eraftpb.MessageType.MsgHeartbeatResponse)
                        .setFrom(this.id)
                        .setTo(to)
                        .setTerm(this.term)
                        .setReject(reject)
                .build());
    }

    private boolean sendAppend(long to) {
        // 获取即将发送的日志的上一条日志的index和term，用于进行一致性检查
        long prevIndex = prs.get(to).next - 1;
        long prevTerm = raftLog.Term(prevIndex);
        // 向节点发送从next下标开始的所有日志
        ArrayList<Eraftpb.Entry> entries = new ArrayList<>();
        int len = raftLog.entries.size();
        for(int i = (int)prevIndex + 1; i < len; ++i) {
            entries.add(raftLog.entries.get(i));
        }
        // 要发送的Message
        Eraftpb.Message msg = Eraftpb.Message.newBuilder()
                .setMsgType(Eraftpb.MessageType.MsgAppend)
                .setFrom(id)
                .setTo(to)
                .setTerm(term)
                .setCommit(raftLog.getCommited())
                .setLogTerm(prevTerm)
                .setIndex(prevIndex)
                .addAllEntries(entries)
                .build();
        msgs.add(msg);
        return true;
    }

    private void sendAppendResponse(long to, boolean reject, long term, long index) {
        Eraftpb.Message msg = Eraftpb.Message.newBuilder()
                .setMsgType(Eraftpb.MessageType.MsgAppendResponse)
                .setFrom(id)
                .setTo(to)
                .setTerm(term)
                .setReject(reject)
                .setLogTerm(term)
                .setIndex(index)
                .build();
        msgs.add(msg);
    }

    private void sendRequestVote(long to, long index, long term) {
        Eraftpb.Message msg = Eraftpb.Message.newBuilder()
                .setMsgType(Eraftpb.MessageType.MsgRequestVote)
                .setFrom(id)
                .setTo(to)
                .setTerm(this.term)
                .setLogTerm(term)
                .setIndex(index)
                .build();
        msgs.add(msg);
    }

    private void sendRequestVoteResponse(long to, boolean reject) {
        Eraftpb.Message msg = Eraftpb.Message.newBuilder()
                .setMsgType(Eraftpb.MessageType.MsgRequestVoteResponse)
                .setFrom(id)
                .setTo(to)
                .setTerm(this.term)
                .setReject(reject)
                .build();
        msgs.add(msg);
    }

    /**
     * Leader广播发送Append RPC
     */
    private void bcastAppend() {
        for (Integer peerId : prs.keySet()) {
            if((int)id == peerId) {
                continue;
            }
            sendAppend(peerId);
        }
    }

    /**
     * Leader广播发送Heartbeat RPC
     */
    private void bcastHeartbeat() {
        for(Integer peerId : prs.keySet()) {
            if(peerId == (int)id) {
                continue;
            }
            sendHeartbeat(peerId);
        }
    }

    /**
     * 时钟定时事件
     */
    public void tick() {
        switch(state) {
            case Follower:
                // Candidate选举超时，重新一轮选举
            case Candidate: {
                tickElection();
                break;
            }
            case Leader: {
                // 定时发起心跳
                tickHeartbeat();
                break;
            }
        }
    }

    /**
     * tick()超时选举判断
     */
    private void tickElection() {
        electionElapsed++;
        // 该节点指定时间超时，没有收到Leader的心跳
        if(electionElapsed >= randomElectionTimeOut) {
            electionElapsed = 0;
            // 发起选举
            step(Eraftpb.Message.newBuilder()
                    .setMsgType(Eraftpb.MessageType.MsgHup)
                    .build());
        }
    }

    /**
     * 定时tick()发起心跳
     */
    private void tickHeartbeat() {
        heartbeatElapsed++;
        if(heartbeatElapsed >= heartbeatTimeOut) {
            heartbeatElapsed = 0;
            step(Eraftpb.Message.newBuilder()
                    .setMsgType(Eraftpb.MessageType.MsgBeat)
                    .build());
        }
    }

    public void step(Eraftpb.Message message) {

        // 如果收到任期号更大的消息，将降级为Follower
        if(message.getTerm() > term) {
            becomeFollower(message.getTerm(), -1);
        }

        switch (state) {
            case Follower: {
                stepFollower(message);
                break;
            }
            case Candidate:{
                stepCandidate(message);
                break;
            }
            case Leader: {
                stepLeader(message);
                break;
            }
        }
    }

    private void stepFollower(Eraftpb.Message message) {
        switch (message.getMsgType()) {
            case MsgHup:{
                // electionElapsed超时，发起选举
                doElection();
                break;
            }
            case MsgBeat:{
                // Follower不会发起心跳
                break;
            }
            case MsgPropose: {
                //
                break;
            }
            case MsgAppend: {
                // 处理Append
                handleAppendEntries(message);
                break;
            }
            case MsgAppendResponse: {
                // 由Leader处理该消息
                break;
            }
            case MsgRequestVote: {
                // 处理投票
                handleRequestVote(message);
                break;
            }
            case MsgRequestVoteResponse: {
                // 由candidate处理该消息
                break;
            }
            case MsgSnapshot: {
                break;
            }
            case MsgHeartbeat: {
                // 处理心跳
                handleHeartbeat(message);
                break;
            }
            case MsgHeartbeatResponse: {
                // 由Leader处理
                break;
            }
            case MsgTransferLeader: {
                break;
            }
            case MsgTimeoutNow: {
                break;
            }
        }
    }

    private void stepCandidate(Eraftpb.Message message) {
        switch (message.getMsgType()) {
            case MsgHup:{
                // 选举超时，发起重新选举
                doElection();
                break;
            }
            case MsgBeat:{
                // Leader发起
                break;
            }
            case MsgPropose: {
                //
                break;
            }
            case MsgAppend: {
                // 收到收到同一任期的Leader的Append，说明是别的Follower优先获取到投票并选举为了Leader
                if(message.getTerm() == this.term) {
                    becomeFollower(message.getTerm(), message.getFrom());
                }
                // 非同一任期，那就是旧的Leader发来的，说明旧的Leader恢复了或者并没有宕机
                handleAppendEntries(message);
                break;
            }
            case MsgAppendResponse: {
                // 由Leader处理
                break;
            }
            case MsgRequestVote: {
                // candidate也会投票，投拒绝票
                handleRequestVote(message);
                break;
            }
            case MsgRequestVoteResponse: {
                // 处理投票
                handleRequestVoteResponse(message);
                break;
            }
            case MsgSnapshot: {
                break;
            }
            case MsgHeartbeat: {
                // 跟Append一样，分为两种情况处理：1.别的Follower先一步选举出Leader 2. 旧的Leader恢复或本身没有宕机
                if(message.getTerm() == term) {
                    becomeFollower(message.getTerm(), message.getFrom());
                }
                handleHeartbeat(message);
                break;
            }
            case MsgHeartbeatResponse: {
                // 由Leader处理
                break;
            }
            case MsgTransferLeader: {
                break;
            }
            case MsgTimeoutNow: {
                break;
            }
        }
    }

    private void stepLeader(Eraftpb.Message message) {
        switch (message.getMsgType()) {
            case MsgHup:{
                // Leader不会发起选举
                break;
            }
            case MsgBeat:{
                // Leader进行心跳广播
                bcastHeartbeat();
                break;
            }
            case MsgPropose: {
                //
                break;
            }
            case MsgAppend: {
                // TODO 会出现Leader收到Append RPC的情况吗？

                break;
            }
            case MsgAppendResponse: {
                handleAppendEntriesResponse(message);
                break;
            }
            case MsgRequestVote: {
                // TODO leader会如何处理投票？
                break;
            }
            case MsgRequestVoteResponse: {
                // candidate
                break;
            }
            case MsgSnapshot: {
                break;
            }
            case MsgHeartbeat: {
                // TODO 会出现Leader收到heartbeat RPC的情况吗？
                break;
            }
            case MsgHeartbeatResponse: {
                // 心跳响应后进行Append
                // TODO 对返回值不需要处理？
                sendAppend(message.getFrom());
                break;
            }
            case MsgTransferLeader: {
                break;
            }
            case MsgTimeoutNow: {
                break;
            }
        }
    }

    private void handleHeartbeat(Eraftpb.Message message) {
        // 来自一个旧任期的Leader的Append消息，消息带上任期号返回使它变成Follower
        if(message.getTerm() != -1 && message.getTerm() < term) {
            sendHeartbeatResponse(message.getFrom(), true);
            return;
        }
        // 正常处理心跳，handle Append的时候会做同样的处理
        // 更新心跳时间为0，并设置一个随机心跳超时时间
        electionElapsed = 0;
        randomElectionTimeOut = electionTimeOut + new Random().nextInt((int)electionTimeOut);
        // 这里已经保证是同一任期的消息了
        // 有几种情况是Leader和Candidate因自身情况不满足而退回Follower，但并不知道集群中谁是Leader
        leaderId = message.getFrom();
        sendHeartbeatResponse(message.getFrom(), false);
    }

    private void handleAppendEntries(Eraftpb.Message message) {
        // 来自一个旧任期的Leader的Append消息
        if(message.getTerm() != -1 && message.getTerm() < term) {
            sendAppendResponse(message.getFrom(), true, term, -1);
            return;
        }

        // 更新心跳时间为0，并设置一个随机心跳超时时间
        electionElapsed = 0;
        randomElectionTimeOut = electionTimeOut + new Random().nextInt((int)electionTimeOut);
        // 这里已经保证是同一任期的消息了
        // 有几种情况是Leader和Candidate因自身情况不满足而退回Follower，但并不知道集群中谁是Leader
        leaderId = message.getFrom();

        // 如果message的index要大于日志的lastIndex，Leader需要回退对于该peer的index再发送
        long lastIndex = raftLog.lastIndex();
        if (message.getIndex() > lastIndex) {
            sendAppendResponse(message.getFrom(), true, -1, lastIndex + 1);
            return;
        }

        // prevIndex在本peer的日志范围内，进行一致性校验
        if (message.getIndex() >= raftLog.firstIndex()) {
            long peerTerm = raftLog.Term(message.getIndex());
            // 一致性校验，如果校验失败，回退找到第一个。反馈index - 1
            if(peerTerm != message.getTerm()) {
                sendAppendResponse(message.getFrom(), true, message.getTerm(), message.getIndex() - 1);
                return;
            }
        }

        // 通过了一致性检查，追加新的日志或者覆盖以前错误的日志
        for (int i = 0; i < message.getEntriesCount(); ++i) {
            Eraftpb.Entry entry = message.getEntries(i);
            if(entry.getIndex() < raftLog.firstIndex()) {
                continue;
            }
            // entry的index属于现存的日志范围内，则是纠正之前错误的日志
            if(entry.getIndex() <= raftLog.lastIndex()) {
                long logTerm = raftLog.Term(entry.getIndex());
                // 如果任期不一样，证明确实是之前错误的日志，选择覆盖
                if(logTerm != entry.getTerm()) {
                    raftLog.entries.set((int)entry.getIndex(), entry);
                    // 错误日志覆盖的时候要确保stabled的正确性
                    raftLog.setStabled(Math.min(raftLog.getStabled(), entry.getIndex() - 1));
                }
            } else {
                // 追加日志
                int n = message.getEntriesCount();
                for(int j = i; j < n; ++j) {
                    raftLog.entries.add(entry);
                }
                // 一次添加完后break，减少重复判断
                break;
            }
        }
        // 更新Leader发来的commit
        raftLog.setCommited(message.getCommit());
        // 成功返回
        sendAppendResponse(message.getFrom(), false, -1, raftLog.lastIndex());
    }

    private void handleAppendEntriesResponse(Eraftpb.Message message) {
        // Follower网络故障一段时间后恢复，任期号是旧的
        if (message.getTerm() != -1 && message.getTerm() < term) {
            return;
        }
        // 拒绝的可能有：
        // 1. 来自旧任期消息，需要拒绝。这种情况不用在这里处理
        // 2. 消息中的日志索引号超出peer的最大日志索引号，需要回退到正确的日志索引号
        // 3. 一致性校验失败的日志，论文使用的是nextIndex - 1然后retry。这里尝试的是将Term和index回传，Leader下次尝试发送的是<index且Term为本任期的。
        if(message.getReject()) {
            long index = message.getIndex();
            // index为-1则是不需要处理的情况
            if(index == -1) {
                return;
            }
            // TODO 需要仔细验证这段逻辑
            // 找到对应term的日志的最后一条开始retry，注意index是上一次的index - 1，保证retry是递减的
            if (message.getTerm() != -1) {
                long logTerm = message.getTerm();
                int idx = Arrays.binarySearch(raftLog.entries.toArray(), (Comparator<Eraftpb.Entry>) (o1, o2) -> (int) (o1.getTerm() - o2.getTerm()));
                if(idx > 0 && raftLog.entries.get(raftLog.toEntryIndex(idx)).getTerm() == message.getTerm()) {
                    index = Math.min(idx, index);
                }
            }
            // retry
            prs.get(message.getFrom()).next = index;
            sendAppend(message.getFrom());
            return;
        }
        // Append日志被正常接收的逻辑：更新每个peer的进度prs，并判断是否收到超过半数成功，更新commit
        if(message.getIndex() > prs.get(message.getFrom()).match) {
            prs.get(message.getFrom()).match = message.getIndex();
            prs.get(message.getFrom()).next = message.getIndex() + 1;
            leaderCommit();
        }
    }

    private void handleRequestVote(Eraftpb.Message message) {
        // 收到了过去任期candidate的message
        if (message.getTerm() != -1 && message.getTerm() < term) {
            sendRequestVoteResponse(message.getFrom(), true);
            return;
        }
        // 已经投过票了，拒绝
        if(vote != -1 && vote != message.getFrom()) {
            sendRequestVoteResponse(message.getFrom(), true);
            return;
        }
        // 根据自己的日志的lastIndex和lastTerm来判断是否要投票
        long lastIndex = raftLog.lastIndex();
        long lastLogTerm = raftLog.Term(lastIndex);
        // 满足这个if条件则说明candidate的日志比自己的旧，拒绝
        if(lastLogTerm > message.getTerm() || (lastLogTerm == message.getLogTerm() && lastIndex > message.getIndex())) {
            sendRequestVoteResponse(message.getFrom(), true);
            return;
        }

        // 满足投票的条件
        // TODO 这里有必要更新electionTimeOut吗？
        vote = message.getFrom();
        sendRequestVoteResponse(message.getFrom(), false);

    }

    private void handleRequestVoteResponse(Eraftpb.Message message) {
        // 收到了过去任期peer的message
        if (message.getTerm() != -1 && message.getTerm() < term) {
            sendRequestVoteResponse(message.getFrom(), true);
            return;
        }
        // 记录投票
        votes.put((int)message.getFrom(), !message.getReject());
        // 每次进行统计，grant是总票数，threshold是阈值
        int grant = 0;
        int threshold = prs.size() / 2;
        for(boolean vote : votes.values()) {
            if(vote) {
                grant++;
            }
        }
        // 超过半数升级为Leader
        if(grant > threshold) {
            becomeLeader();
        }
        // 超时会重新一轮选举，收到Leader的心跳会变成Follower
    }

    private void leaderCommit() {
        long[] match = new long[prs.size()];
        int i = 0;
        for(long id : prs.keySet()) {
            long m = prs.get(id).match;
            match[i] = m;
            i++;
        }
        Arrays.sort(match);
        long n = match[(prs.size() - 1) / 2];

        if(n > raftLog.getCommited()) {
            // TODO 这里需要验证n对应log的term吗
            raftLog.setCommited(n);
            // 再广播append，更新commited
            bcastAppend();
        }
    }

    private void doElection() {
        becomeCandidate();
        // 重置选举超时时间，这里跟Follower用的同一个选举超时时间
        // 选举超时仍会发起新的一轮选举
        electionElapsed = 0;
        randomElectionTimeOut = electionTimeOut + new Random().nextInt((int)electionTimeOut);

        // 向所有其余peer节点发送vote
        long lastIndex = raftLog.lastIndex();
        long lastLogTerm = raftLog.Term(lastIndex);

    }
}
