package com.aurorain.shardkv.web.util;

import com.aurorain.shardkv.tso.TimestampOracle;
import org.springframework.stereotype.Component;

@Component
public class TimestampOracleUtil {

    private final TimestampOracle timestampOracle = new TimestampOracle();

    public TimestampOracle getTimestampOracle() {
        return timestampOracle;
    }
}
