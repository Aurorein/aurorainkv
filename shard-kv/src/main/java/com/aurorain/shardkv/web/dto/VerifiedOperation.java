package com.aurorain.shardkv.web.dto;

import com.aurorain.shardkv.common.CommandType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public
class VerifiedOperation {
    private int logIndex;
    private CommandType type;
    private String key;
    private String observedValue;
    private boolean isValid;
    private String expectedValue;
    private int term;
    private int clientId;
    private int seqId;
}
