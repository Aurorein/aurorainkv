package com.aurorain.shardkv.web.dto;

import lombok.Data;

@Data
public class BatchOperation {
    private String type;
    private String key;
    private String value;
}
