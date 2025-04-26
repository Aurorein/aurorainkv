package com.aurorain.raft.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class InstallSnapshotResponse implements Serializable {
    private int term;
}
