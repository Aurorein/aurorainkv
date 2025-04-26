package com.aurorain.shardmaster.common;

import com.aurorain.shardmaster.model.dto.dto.ShardCommandResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命令上下文，KVServer 记录成功提交的命令
 *
 * @author aurorain
 * @version 1.0
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ShardCommandContext {

    /**
     * 序列 id，client 每发送一个命令 +1
     */
    private int seqId;

    /**
     * id 对应的 client 发送的命令的 response
     */
    private ShardCommandResponse response;

}
