package com.aurorain.shardkv.model;

import lombok.Data;

import java.io.Serializable;
import java.util.StringJoiner;

@Data
public class MigrateArgs implements Serializable {
    private int shard;

    private int configNum;

    @Override
    public String toString() {
        return new StringJoiner(", ", MigrateArgs.class.getSimpleName() + "[", "]")
                .add("shard=" + shard)
                .add("configNum=" + configNum)
                .toString();
    }

    // 将字符串解析为对象
    public static MigrateArgs fromString(String str) {
        MigrateArgs args = new MigrateArgs();
        String[] parts = str.replaceAll("MigrateArgs\\[|\\]", "").split(", ");
        for (String part : parts) {
            String[] keyValue = part.split("=");
            if (keyValue.length == 2) {
                String key = keyValue[0];
                String value = keyValue[1];
                switch (key) {
                    case "shard":
                        args.setShard(Integer.parseInt(value));
                        break;
                    case "configNum":
                        args.setConfigNum(Integer.parseInt(value));
                        break;
                    default:
                        // 忽略未知字段
                        break;
                }
            }
        }
        return args;
    }
}
