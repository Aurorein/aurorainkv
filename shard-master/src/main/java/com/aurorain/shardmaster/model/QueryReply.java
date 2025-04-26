package com.aurorain.shardmaster.model;

import com.aurorain.shardmaster.ShardConfig;
import lombok.Data;

@Data
public class QueryReply extends Reply{
    ShardConfig config;
}
