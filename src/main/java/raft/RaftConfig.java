package raft;

import java.util.List;

public class RaftConfig {
    long id;

    List<Integer> peers;

    long electionTick;

    long heartbeatTick;

    RaftStorage storage;

    long applied;
}
