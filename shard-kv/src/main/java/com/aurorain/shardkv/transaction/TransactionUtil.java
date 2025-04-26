package com.aurorain.shardkv.transaction;

public class TransactionUtil {

    public static String enCodeKey(String key, long ts) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        return key + ":" + ts;
    }

    public static String decodeKey(String userKey) {
        if (userKey == null || userKey.isEmpty()) {
            throw new IllegalArgumentException("UserKey cannot be null or empty");
        }
        int separatorIndex = userKey.lastIndexOf(":");
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("Invalid userKey format");
        }
        return userKey.substring(0, separatorIndex);
    }

    public static long decodeTs(String userKey) {
        if (userKey == null || userKey.isEmpty()) {
            throw new IllegalArgumentException("UserKey cannot be null or empty");
        }
        int separatorIndex = userKey.lastIndexOf(":");
        if (separatorIndex == -1) {
            throw new IllegalArgumentException("Invalid userKey format");
        }
        try {
            return Long.parseLong(userKey.substring(separatorIndex + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid timestamp format in userKey", e);
        }
    }
        // 假设 PhysicalShiftBits 是一个常量，表示逻辑时间占用的位
    private static final int PhysicalShiftBits = 18;

        // 提取时间戳中的物理时间部分
    public static long physicalTime(long ts) {
        return ts >> PhysicalShiftBits;
    }


}
