package com.aurorain.common;

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
    GC(7, "GC");



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
