package com.aurorain.shardkv.constant;

public interface TimeConstant {

    int RETRY_TIMEOUT = 1;

    int CMD_TIMEOUT = 2000;

    int GAP_TIME = 5;

    int APPLIER_TIME = 10;

    int SNAPSHOT_GAP_TIME = 10;

    int PULLCONFIG_GAP_TIME = 1000;

    int PULLSHARDLOOP_GAP_TIME = 1000;

    int GARBAGESLOOP_GAP_TIME = 2000;
}