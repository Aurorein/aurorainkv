package com.aurorain.model.dto.dto;


import com.aurorain.model.Args;
import lombok.Data;

import java.io.Serializable;


@Data
public class ShardCommandRequest implements Serializable {

    /**
     * 命令
     */
    private Args args;

}
