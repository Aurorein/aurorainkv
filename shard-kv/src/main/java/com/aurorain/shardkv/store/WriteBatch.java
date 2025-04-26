package com.aurorain.shardkv.store;

import com.aurorain.shardkv.common.CommandType;
import lombok.Getter;

import java.util.List;

@Getter
public class WriteBatch {
    private List<RocksDBEntry> entries;

    public int size;

    public void setCF(String cf, CommandType type, String key, String value) {
        entries.add(new RocksDBEntry(key, value, cf, type));
        size += key.length() + value.length();
    }

    public static WriteBatch getWriteBatch(List<RocksDBEntry> list) {
        WriteBatch writeBatch = new WriteBatch();
        for(RocksDBEntry entry : list) {
            writeBatch.setCF(entry.getCf(), entry.getType(), entry.getKey(), entry.getValue());
        }
        return writeBatch;
    }
}
