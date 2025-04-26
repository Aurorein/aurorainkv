package com.aurorain.shardmaster.web.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AddShardMasterClusterReq implements Serializable {
    int n;
}
