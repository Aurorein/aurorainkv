package com.aurorain.shardkv.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Write {
    long startTs;

    int writeKind;

    // 自定义 parse 方法
    public static Write parse(String str) {
        Write write = new Write();
        String[] parts = str.split(",");
        if (parts.length == 2) {
            write.setStartTs(Long.parseLong(parts[0].trim()));
            write.setWriteKind(Integer.parseInt(parts[1].trim()));
        } else {
            throw new IllegalArgumentException("Invalid string format for Write object");
        }
        return write;
    }
}
