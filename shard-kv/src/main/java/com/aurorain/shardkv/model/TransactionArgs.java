package com.aurorain.shardkv.model;

import com.aurorain.shardkv.model.dto.TransactionOper;
import com.aurorain.shardkv.store.RocksDBEntry;
import lombok.Data;
import java.util.List;

import java.io.Serializable;

@Data
public class TransactionArgs implements Serializable {

    private List<TransactionOper> txns;
}
