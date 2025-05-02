package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class WriteConflict implements Serializable {
    private long startTs;
    private long conflictTs;
    private String key;
    private String primary;

    public WriteConflict(long startTs, long conflictTs, String key, String primary) {
        this.startTs = startTs;
        this.conflictTs = conflictTs;
        this.key = key;
        this.primary = primary;
    }
}
