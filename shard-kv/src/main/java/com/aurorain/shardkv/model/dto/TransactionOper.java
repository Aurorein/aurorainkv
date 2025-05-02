package com.aurorain.shardkv.model.dto;

import com.aurorain.shardkv.store.RocksDBEntry;
import lombok.Data;
import java.util.List;

import java.io.Serializable;

@Data
public class TransactionOper implements Serializable {

    private long txnId;
    private String primary;
    private List<RocksDBEntry> entries;
    private int op;// 1=execute 2=crash 3=get

}
