package com.aurorain.shardkv.model.dto;

import com.aurorain.shardkv.model.Command;
import lombok.Data;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class CommandRequest implements Serializable {

    /**
     * 命令
     */
    private Command command;


}
