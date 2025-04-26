package com.aurorain.shardmaster.web.util;

import com.aurorain.shardmaster.ShardClient;
import com.aurorain.commonmodule.exceptions.ShardClientEmptyException;
import org.springframework.stereotype.Component;

@Component
public class ShardMasterWebUtil {
    private volatile ShardClient shardClient;

    public ShardClient setShardClient(ShardClient shardClient) {
        if (this.shardClient == null) {
            synchronized (this) {
                if (this.shardClient == null) {
                    this.shardClient = shardClient;
                }
            }
        }
        return shardClient;
    }

    public ShardClient getShardClient() {
        ShardClient client = this.shardClient; // 读取volatile变量到本地
        if (client == null) {
            throw new ShardClientEmptyException();
        }
        return client;
    }
}
