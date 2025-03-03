package com.aurorain.model;

import com.aurorain.common.CommandType;
import lombok.Data;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class Command implements Serializable {

    /**
     * 命令类型
     */
    private CommandType type;

    /**
     * 键值
     */
    private String key;

    /**
     * 值
     */
    private String value;

    /**
     * 客户端 id
     */
    private int clientId;

    /**
     * 序列 id
     */
    private int seqId;

}
