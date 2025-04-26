package com.aurorain.shardkv.web.util;

import com.aurorain.shardkv.ShardKVRaftApplication;
import com.aurorain.shardkv.KVRaftConfig;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Data
public class ShardKvConfigUtil {

    private Map<Integer, KVRaftConfig> configs;

    private Map<Integer, ShardKVRaftApplication> applications;

    public ShardKvConfigUtil() {
        configs = new HashMap<>();
        applications = new HashMap<>();
    }
}
