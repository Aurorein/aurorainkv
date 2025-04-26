package com.aurorain.shardkv.model;

import com.aurorain.shardkv.common.CommandType;
import lombok.Data;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class Command<T> implements Serializable {

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
    private T value;

    /**
     * 客户端 id
     */
    private int clientId;

    /**
     * 序列 id
     */
    private int seqId;

}
