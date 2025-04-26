package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class CheckTxnStatusResponse extends CommandResponse implements Serializable {
    private long commitTs;

    private int action;
}
