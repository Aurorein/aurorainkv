package com.aurorain.shardkv.common;

/**
 * 命令类型
 *
 * @author aurorain
 * @version 1.0
 */
public enum CommandType {

    GET(0, "get"),
    PUT(1, "put"),
    APPEND(2, "append"),
    UPDATECONFIG(3, "updateConfig"),
    SHARDMIGRATE(4, "shardMigration"),
    UPDATEDATEBASE(5, "updateDatabase"),
    GARBAGESCOLLECT(6, "garbagesCollect"),
    GC(7, "GC"),
    DELETE(8, "delete"),
    WRITE(9, "write"),
    LOCK(10, "lock"),
    DELLOCK(11, "deleteLock"),
    SNAP(12, "snap"),
    WRITEBATCH(13, "writeBatch");




    private final int code;

    private final String description;

    CommandType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

}
