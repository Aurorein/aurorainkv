package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ResolveLockArgs implements Serializable {
    private long startTs;
    private long commitTs;
}
