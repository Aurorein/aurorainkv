package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CheckTxnStatusReply implements Serializable {
    private long commitTs;
    private long lockTtl;
}
