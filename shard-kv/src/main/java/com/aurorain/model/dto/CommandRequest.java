package com.aurorain.model.dto;

import com.aurorain.model.Command;
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
