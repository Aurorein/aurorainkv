package com.aurorain.service;

import com.aurorain.ShardConfig;
import com.aurorain.model.GarbagesCollectArgs;
import com.aurorain.model.GarbagesCollectReply;
import com.aurorain.model.MigrateArgs;
import com.aurorain.model.MigrateReply;
import com.aurorain.model.dto.CommandRequest;
import com.aurorain.model.dto.CommandResponse;

/**
 * @author aurorain
 * @version 1.0
 */
public interface KVServerService {

    /**
     * 响应来自 client 的命令
     *
     * @param request 请求体
     * @return 响应体
     */
    CommandResponse requestCommand(CommandRequest request);

    CommandResponse shardMigration(CommandRequest request);

    CommandResponse garbagesCollect(CommandRequest request);

}
