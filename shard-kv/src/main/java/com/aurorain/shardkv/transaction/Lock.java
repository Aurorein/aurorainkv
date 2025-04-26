package com.aurorain.shardkv.transaction;

import com.aurorain.shardkv.constant.CFConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rocksdb.RocksIterator;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Lock {
    private String primary;
    private long ts;
    private long ttl;
    private int kind;

    @Override
    public String toString() {
        return String.join(",", primary, String.valueOf(ts), String.valueOf(ttl), String.valueOf(kind));
    }

    /**
     * 从字符串中解析出 Lock 对象
     *
     * @param str 字符串表示的 Lock 对象
     * @return 解析后的 Lock 对象
     * @throws IllegalArgumentException 如果字符串格式无效
     */
    public static Lock parse(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be null or empty");
        }

        String[] parts = str.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid string format for Lock object");
        }

        Lock lock = new Lock();
        try {
            lock.setPrimary(parts[0]);
            lock.setTs(Long.parseLong(parts[1]));
            lock.setTtl(Long.parseLong(parts[2]));
            lock.setKind(Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number format in input string", e);
        }

        return lock;
    }

    public boolean isLockedFor(String key, long txnStartTs) {
        if(this.ts <= txnStartTs) {
            return true;
        }
        return false;
    }

    public List<KLPair> allLocksForTxn(MvccTxn txn) {
        List<KLPair> res = new ArrayList<>();
        RocksIterator iterator = txn.reader.iterCf(CFConstants.CfLock);

        for(;iterator.isValid();iterator.next()) {
            String value = new String(iterator.value());
            Lock lock = parse(value);
            if(lock.ts == txn.startTs) {
                res.add(new KLPair(new String(iterator.key()), lock));
            }
        }
        return res;
    }

}
