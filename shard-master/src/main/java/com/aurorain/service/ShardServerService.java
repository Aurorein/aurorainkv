package com.aurorain.service;

import com.aurorain.model.dto.dto.ShardCommandRequest;
import com.aurorain.model.dto.dto.ShardCommandResponse;

public interface ShardServerService {

    ShardCommandResponse requestCommand(ShardCommandRequest request);

}
