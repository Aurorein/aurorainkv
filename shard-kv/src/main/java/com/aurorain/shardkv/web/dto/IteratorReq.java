package com.aurorain.shardkv.web.dto;

import lombok.Data;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.Serializable;

@Data
public class IteratorReq implements Serializable {
    private int gid; // groupId
    private int sid; // serverId
}
