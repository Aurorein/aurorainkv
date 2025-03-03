package com.aurorain.model;

import com.aurorain.common.ShardCommandContext;
import com.aurorain.store.KV;
import com.aurorain.store.RocksDBKV;
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
