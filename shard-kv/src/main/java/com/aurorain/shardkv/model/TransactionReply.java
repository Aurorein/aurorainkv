package com.aurorain.shardkv.model;

import lombok.Data;
import java.util.List;

import java.io.Serializable;

@Data
public class TransactionReply implements Serializable {

    private List<String> logs;
}
