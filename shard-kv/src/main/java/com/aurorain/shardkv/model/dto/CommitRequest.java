package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class CommitRequest extends CommandRequest implements Serializable {
    private long startTs;

    private long commitTs;

    private List<String> keys;
}
