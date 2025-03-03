package com.aurorain.model;

import com.aurorain.ShardConfig;
import lombok.Data;

@Data
public class QueryReply extends Reply{
    ShardConfig config;
}
