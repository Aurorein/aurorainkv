package com.aurorain.shardmaster.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MoveArgs extends Args {

    private int shard;

    private int gid;
}
