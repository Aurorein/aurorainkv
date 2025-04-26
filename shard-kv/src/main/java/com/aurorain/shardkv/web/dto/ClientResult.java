package com.aurorain.shardkv.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ClientResult {
    private int clientId;
    private String errorType;
    private String message;
}
