package com.aurorain;

import com.aurorain.constant.ShardConstant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Data
public class ShardConfig implements Serializable {
    private int num;

    private int[] shards = new int[ShardConstant.NShard];

    private Map<Integer, List<Integer>> groups;

    public ShardConfig clone() {
        ShardConfig shardConfig = new ShardConfig();

        // 复制基本类型
        shardConfig.num = this.num;

        // 复制数组（深拷贝）
        shardConfig.shards = this.shards.clone();

        // 复制Map（深拷贝）
        if (this.groups != null) {
            shardConfig.groups = new HashMap<>();
            for (Map.Entry<Integer, List<Integer>> entry : this.groups.entrySet()) {
                // 复制List（深拷贝）
                List<Integer> copiedList = new ArrayList<>(entry.getValue());
                shardConfig.groups.put(entry.getKey(), copiedList);
            }
        } else {
            shardConfig.groups = null;
        }

        return shardConfig;
    }

    // 将对象转换为JSON字符串
    public String toJsonString() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    // 将JSON字符串解码为对象
    public static ShardConfig fromJsonString(String json)  {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(json, ShardConfig.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
