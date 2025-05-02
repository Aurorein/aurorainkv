package com.aurorain.shardkv.constant;

public interface TxnConstants {
    int ROLLBACK = 1;

    int ABORT = 2;

    int ACTION_TTLEXPIREROLLBACK = 3;

    int ACTION_LOCKNOTEXISTSROLLBACK = 4;

    int ACTION_NOACTION = 5;

    int READ_LOCKCONFLICT = 6;

    int ACTION_COMMIT = 7;

    int ACTION_ROLLBACK = 8;

}
