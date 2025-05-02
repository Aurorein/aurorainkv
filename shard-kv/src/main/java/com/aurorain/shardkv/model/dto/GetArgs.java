package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GetArgs implements Serializable {

    private long ts;
}
