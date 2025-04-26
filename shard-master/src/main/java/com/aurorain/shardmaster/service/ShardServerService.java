package com.aurorain.shardmaster.service;

import com.aurorain.shardmaster.model.dto.dto.ShardCommandRequest;
import com.aurorain.shardmaster.model.dto.dto.ShardCommandResponse;

public interface ShardServerService {

    ShardCommandResponse requestCommand(ShardCommandRequest request);

}
