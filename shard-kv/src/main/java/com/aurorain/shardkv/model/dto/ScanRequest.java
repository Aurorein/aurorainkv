package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ScanRequest extends CommandRequest implements Serializable {

    private long ts;
    private String startKey;
    private int limit;

}
