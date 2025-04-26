package com.aurorain.shardkv.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClientVerification {
    private int clientId;
    private int operationCount;
    private String finalValue;
    private boolean valid;
}
