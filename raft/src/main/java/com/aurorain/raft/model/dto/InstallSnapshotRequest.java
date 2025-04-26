package com.aurorain.raft.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class InstallSnapshotRequest implements Serializable {

    private int term;

    private int leaderId;

    private int lastIncludedIndex;

    private int lastIncludedTerm;

    private byte[] data;

}
