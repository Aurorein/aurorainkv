package com.aurorain.shardmaster.model.dto.dto;


import com.aurorain.shardmaster.model.Args;
import lombok.Data;

import java.io.Serializable;


@Data
public class ShardCommandRequest implements Serializable {

    /**
     * 命令
     */
    private Args args;

}
