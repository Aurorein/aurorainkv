package com.aurorain.shardkv.web.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class GetReq implements Serializable {
    private String key;
}
