package com.aurorain.shardmaster.web.util;

import com.aurorain.shardmaster.ShardMasterApplication;
import com.aurorain.shardmaster.config.ShardServerConfig;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component
public class ShardMasterConfigUtil {
    private final AtomicReference<ShardMasterApplication> applicationRef = new AtomicReference<>();
    private final AtomicReference<ShardServerConfig> configRef = new AtomicReference<>();

    public ShardMasterApplication getApplication() {
        return applicationRef.get();
    }

    public void setApplication(ShardMasterApplication application) {
        applicationRef.set(application);
    }

    public ShardServerConfig getConfig() {
        return configRef.get();
    }

    public void setConfig(ShardServerConfig config) {
        configRef.set(config);
    }
}
