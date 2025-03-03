package com.aurorain.common;

public enum ShardCommandType {

    QUERY(0, "Query"),

    JOIN(1, "Join"),

    LEAVE(2, "Leave"),

    MOVE(3, "Move");

    private final int code;

    private final String description;

    ShardCommandType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {return code;}

    public String getDescription() {return description;}
}
