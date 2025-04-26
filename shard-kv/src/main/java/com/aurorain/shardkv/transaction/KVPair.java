package com.aurorain.shardkv.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KVPair {
    private String key;

    private String value;
}
