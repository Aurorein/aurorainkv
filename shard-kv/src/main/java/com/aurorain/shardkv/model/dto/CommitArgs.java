package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class CommitArgs implements Serializable {
    private long startTs;

    private long commitTs;

    private List<String> keys;
}
