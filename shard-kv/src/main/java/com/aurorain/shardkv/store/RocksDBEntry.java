package com.aurorain.shardkv.store;

import com.aurorain.shardkv.common.CommandType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RocksDBEntry implements Serializable {
    private String key;

    private String value;

    private String cf;

    private CommandType type;
}
