package com.aurorain.shardkv.model.dto;

import com.aurorain.shardkv.store.RocksDBEntry;
import lombok.Data;

import java.util.List;
import java.io.Serializable;

@Data
public class PreWriteArgs implements Serializable {

    private long startTs;

    private List<RocksDBEntry> entries;

    private String primary;

    private long lockTtl;

}
