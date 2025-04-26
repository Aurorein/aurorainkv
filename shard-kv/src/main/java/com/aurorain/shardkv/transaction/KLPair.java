package com.aurorain.shardkv.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KLPair {
    private String key;

    private Lock lock;
}
