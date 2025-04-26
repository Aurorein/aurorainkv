package com.aurorain.raft.model;

import lombok.Data;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class RaftStatePersist {

    private int currentTerm;

    private int votedFor;

    private Entry[] logs;

}
