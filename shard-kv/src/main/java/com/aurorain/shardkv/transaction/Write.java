package com.aurorain.shardkv.transaction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Write {
    long startTs;

    int writeKind;

    // 自定义 parse 方法
    public static Write parse(String str) {
        // 去掉前缀 "Write(" 和后缀 ")"
        if (!str.startsWith("Write(") || !str.endsWith(")")) {
            throw new IllegalArgumentException("Invalid string format for Write object");
        }
        String content = str.substring(6, str.length() - 1).trim();

        // 按逗号分割内容
        String[] parts = content.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid string format for Write object");
        }

        // 初始化 Write 对象
        Write write = new Write();

        try {
            // 解析 startTs
            String startTsPart = parts[0].trim();
            if (!startTsPart.startsWith("startTs=")) {
                throw new IllegalArgumentException("Missing 'startTs=' in input string");
            }
            write.setStartTs(Long.parseLong(startTsPart.substring(8).trim()));

            // 解析 writeKind
            String writeKindPart = parts[1].trim();
            if (!writeKindPart.startsWith("writeKind=")) {
                throw new IllegalArgumentException("Missing 'writeKind=' in input string");
            }
            write.setWriteKind(Integer.parseInt(writeKindPart.substring(10).trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Failed to parse numeric values from input string", e);
        }

        return write;
    }
}
