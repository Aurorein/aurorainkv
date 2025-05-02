package com.aurorain.shardkv.store;

import com.aurorain.shardkv.common.CommandType;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
public class WriteBatch implements Serializable {
    private List<RocksDBEntry> entries = new ArrayList<>();

    public int size;

    public void setCF(String cf, CommandType type, String key, String value) {
        entries.add(new RocksDBEntry(key, value, cf, type));
        size += key.length() + ((value != null) ? value.length() : 0);
    }

    public static WriteBatch getWriteBatch(List<RocksDBEntry> list) {
        if(list == null) return null;
        WriteBatch writeBatch = new WriteBatch();
        for(RocksDBEntry entry : list) {
            writeBatch.setCF(entry.getCf(), entry.getType(), entry.getKey(), entry.getValue());
        }
        return writeBatch;
    }
}
