package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LockInfo implements Serializable {

    private String primary;
    private long lockTs;
    private String key;
    private long lockTtl;

    public LockInfo(String primary, long lockTs, String key, long lockTtl) {
        this.primary = primary;
        this.lockTs = lockTs;
        this.key = key;
        this.lockTtl = lockTtl;
    }

    public LockInfo() {
    }
}
