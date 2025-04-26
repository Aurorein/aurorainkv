package com.aurorain.shardkv.web.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AddClusterReq implements Serializable {
    int gid;
    int[] servers;
    int maxRaftState;
}
