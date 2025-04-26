package com.aurorain.shardmaster.model;

import com.aurorain.shardmaster.common.ShardCommandType;
import lombok.Data;

import java.io.Serializable;

@Data
public class Args implements Serializable{

    /**
     * 命令类型
     */
    protected ShardCommandType type;

//    /**
//     * 键值
//     */
//    private String key;
//
//    /**
//     * 值
//     */
//    private String value;
//
    /**
     * 客户端 id
     */
    protected int clientId;

    /**
     * 序列 id
     */
    protected int seqId;

}
