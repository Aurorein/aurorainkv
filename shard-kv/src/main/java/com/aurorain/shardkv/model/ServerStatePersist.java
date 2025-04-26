package com.aurorain.shardkv.model;

import com.aurorain.shardmaster.common.ShardCommandContext;
import com.aurorain.shardkv.store.KV;
import com.aurorain.shardkv.store.RocksDBKV;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class ServerStatePersist {

    private KV store;

    private Map<Integer, ShardCommandContext> lastCmdContext;

    public ServerStatePersist(int me) {
        store = new RocksDBKV("D:/Papers/graduation/aurorainkv/metadb" + me);
        lastCmdContext = new HashMap<>();
    }


}
