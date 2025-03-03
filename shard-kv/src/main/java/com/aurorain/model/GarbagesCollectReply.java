package com.aurorain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.Serializable;

@Data
public class GarbagesCollectReply implements Serializable {
    private boolean isLeader;

    private boolean isOk;

    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to convert GarbagesCollectArgs to JSON string", e);
        }
    }

    // 将 JSON 字符串解析为对象
    public static GarbagesCollectReply fromString(String json) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, GarbagesCollectReply.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON string to GarbagesCollectReply", e);
        }
    }
}
