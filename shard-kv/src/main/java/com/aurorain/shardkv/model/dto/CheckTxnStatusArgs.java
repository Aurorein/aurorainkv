package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CheckTxnStatusArgs implements Serializable {
    private long lockTs;
    private String primaryKey;
    private long commitTs;
}
