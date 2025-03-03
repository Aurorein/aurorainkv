package com.aurorain.model;

import com.aurorain.service.ShardServerService;
import lombok.Data;

@Data
public class ShardClerk {
    private ShardServerService[] services;

    private int leaderId;

    private int clientId;

    private int seqId;

}
