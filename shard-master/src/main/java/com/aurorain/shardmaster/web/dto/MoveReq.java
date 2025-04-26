package com.aurorain.shardmaster.web.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class MoveReq implements Serializable {
    private int shard;
    private int gid;
}
