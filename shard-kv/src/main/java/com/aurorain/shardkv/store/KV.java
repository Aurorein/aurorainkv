package com.aurorain.shardkv.store;

import com.aurorain.shardkv.model.Command;

import java.util.Iterator;
import java.util.Map;

public interface KV {
    boolean put(String key, String value);

    boolean append(String key, String value);

    String get(String key);

    boolean delete(String key);

    RocksDBReader openReader();

    String opt(Command command);

    void opt(WriteBatch writeBatch) throws Exception;

    Iterator<Map.Entry<String, String>> iterator();
}
