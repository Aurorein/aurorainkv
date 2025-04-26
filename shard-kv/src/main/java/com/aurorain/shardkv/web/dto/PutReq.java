package com.aurorain.shardkv.web.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PutReq implements Serializable {
    private String key;
    private String value;
}
