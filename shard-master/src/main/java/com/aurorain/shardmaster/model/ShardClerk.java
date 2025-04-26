package com.aurorain.shardmaster.model;

import com.aurorain.shardmaster.service.ShardServerService;
import lombok.Data;

@Data
public class ShardClerk {
    private ShardServerService[] services;

    private int leaderId;

    private int clientId;

    private int seqId;

}
