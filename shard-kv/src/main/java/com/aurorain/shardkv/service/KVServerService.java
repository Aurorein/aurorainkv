package com.aurorain.shardkv.service;

import com.aurorain.shardkv.model.dto.CommandResponse;
import com.aurorain.shardkv.model.dto.CommandRequest;

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

    CommandResponse kvPrewrite(CommandRequest request);

    CommandResponse kvCommit(CommandRequest request);

    CommandResponse kvGet(CommandRequest request);

//    CommandResponse kvScan(CommandRequest request);

    CommandResponse kvCheckTxnStatus(CommandRequest request);

    CommandResponse KvBatchRollback(CommandRequest request);

    CommandResponse kvResolveLock(CommandRequest request);

}
