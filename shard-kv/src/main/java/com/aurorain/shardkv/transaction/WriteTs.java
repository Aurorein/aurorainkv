package com.aurorain.shardkv.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WriteTs {
    private Write write;

    private long ts;

}
