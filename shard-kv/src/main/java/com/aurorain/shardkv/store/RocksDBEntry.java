package com.aurorain.shardkv.store;

import com.aurorain.shardkv.common.CommandType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocksDBEntry {
    private String key;

    private String value;

    private String cf;

    private CommandType type;
}
