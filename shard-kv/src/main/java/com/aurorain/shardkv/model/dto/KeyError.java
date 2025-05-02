package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class KeyError implements Serializable {

    private LockInfo lockInfo;
    private WriteConflict writeConflict;

    public KeyError(LockInfo lockInfo) {
        this.lockInfo = lockInfo;
    }

    public KeyError(WriteConflict writeConflict) {
        this.writeConflict = writeConflict;
    }
}
