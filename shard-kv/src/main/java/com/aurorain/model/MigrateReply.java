package com.aurorain.model;

import com.aurorain.common.CommandContext;
import com.aurorain.common.ShardCommandContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;

@Data
public class MigrateReply implements Serializable {
    private boolean isLeader;
    private int shard;
    private int configNum;

    private HashMap<String, String> database;
    private HashMap<Integer, CommandContext> clientReqId;

    // 将对象转化为 JSON 字符串
    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert MigrateReply to JSON string", e);
        }
    }

    // 将 JSON 字符串解析为对象
    public static MigrateReply fromString(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, MigrateReply.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON string to MigrateReply", e);
        }
    }
}
