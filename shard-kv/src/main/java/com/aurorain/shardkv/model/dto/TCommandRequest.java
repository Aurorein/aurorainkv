package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class TCommandRequest extends CommandRequest implements Serializable {

    private long ts;
}
