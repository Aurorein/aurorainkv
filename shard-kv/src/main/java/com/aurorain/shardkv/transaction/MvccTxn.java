package com.aurorain.shardkv.transaction;

import com.aurorain.shardkv.common.CommandType;
import com.aurorain.shardkv.constant.CFConstants;
import com.aurorain.shardkv.constant.WriteConstants;
import com.aurorain.shardkv.store.RocksDBEntry;
import com.aurorain.shardkv.store.RocksDBReader;
import lombok.Getter;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.util.ArrayList;
import java.util.List;

@Getter
public class MvccTxn {
    long startTs;

    RocksDBReader reader;

    List<RocksDBEntry> writes;

    public MvccTxn(long startTs, RocksDBReader reader) {
        this.startTs = startTs;
        this.reader = reader;
        this.writes = new ArrayList<>();
    }

    public void putWrite(String key, long ts, Write write) {
        RocksDBEntry entry = new RocksDBEntry(TransactionUtil.enCodeKey(key, ts), write.toString(), CFConstants.CfWrite, CommandType.WRITE);
        writes.add(entry);
    }

    public void putValue(String key, String value) {
        RocksDBEntry entry = new RocksDBEntry(TransactionUtil.enCodeKey(key, this.startTs), value, CFConstants.CfDefault, CommandType.PUT);
        writes.add(entry);
    }

    public void deleteValue(String key) {
        RocksDBEntry entry = new RocksDBEntry(TransactionUtil.enCodeKey(key, this.startTs), null, CFConstants.CfDefault, CommandType.DELETE);
        writes.add(entry);
    }

    public String getValue(String key) {
        // 获取 CfWrite 列族的迭代器
        RocksIterator iterator = reader.iterCf(CFConstants.CfWrite);
        try {
            // 定位到小于等于 EncodeKey(key, startTs) 的最新的键
            iterator.seekForPrev(TransactionUtil.enCodeKey(key, startTs).getBytes());

            // 如果没有找到符合条件的键，返回 null
            if (!iterator.isValid()) {
                return null;
            }

            // 获取当前键值对
            byte[] gotKey = iterator.key();
            String userKey = TransactionUtil.decodeKey(new String(gotKey));

            // 检查键是否匹配
            if (!key.equals(userKey)) {
                return null;
            }

            // 获取写入记录的值
            byte[] valueBytes = iterator.value();
            Write write = Write.parse(new String(valueBytes));

            // 如果写入记录是删除操作，返回 null
            if (write.getWriteKind() == WriteConstants.WRITE_KIND_DELETE) {
                return null;
            }

            // 从 CfDefault 列族中获取实际的值
            try {
                return reader.getCf(CFConstants.CfDefault, TransactionUtil.enCodeKey(key, write.getStartTs()));
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        } finally {
            // 关闭迭代器
            iterator.close();
        }
    }

    public void putLock(String key, Lock lock) {
        RocksDBEntry entry = new RocksDBEntry(key, lock.toString(), CFConstants.CfLock, CommandType.LOCK);
        writes.add(entry);
    }

    public void deleteLock(String key) {
        RocksDBEntry entry = new RocksDBEntry(key, null, CFConstants.CfLock, CommandType.DELLOCK);
        writes.add(entry);
    }

    public Lock getLock(String key) {
        String value;
        try {
            value = reader.getCf(CFConstants.CfLock, key);
            if(value == null) {
                return null;
            }
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        Lock lock = Lock.parse(value);
        return lock;

    }

    /**
     * 获取当前事务下，传入key的最新Write
     * @param key
     * @return
     */
    public WriteTs currentWrite(String key) {
        // 获取 CfWrite 列族的迭代器
        RocksIterator iterator = reader.iterCf(CFConstants.CfWrite);
        try {
            // 定位到第一个大于等于 EncodeKey(key, ^uint64(0)) 的键
            iterator.seekForPrev(TransactionUtil.enCodeKey(key, Long.MAX_VALUE).getBytes());

            // 遍历迭代器
            while (iterator.isValid()) {
                // 获取当前键值对
                byte[] gotKey = iterator.key();
                String userKey = TransactionUtil.decodeKey(new String(gotKey));

                // 如果键不匹配，则返回 null
                if (!userKey.equals(key)) {
                    return null;
                }

                // 获取写入记录的值
                byte[] valueBytes = iterator.value();
                Write write = Write.parse(new String(valueBytes));

                // 如果写入记录的开始时间戳等于当前事务的开始时间戳，则返回结果
                if (write.getStartTs() == startTs) {
                    long commitTs = TransactionUtil.decodeTs(new String(gotKey));
                    return new WriteTs(write, commitTs);
                }

                // 移动到上一个键
                iterator.prev();
            }
        } finally {
            // 关闭迭代器
            iterator.close();
        }

        return null;
    }

    /**
     * 获取传入key的最新Write
     * @param key
     * @return
     */
    public WriteTs mostRecentWrite(String key) {
        // 获取 CfWrite 列族的迭代器
        RocksIterator iterator = reader.iterCf(CFConstants.CfWrite);
        try {
            // 定位到第一个大于等于 EncodeKey(key, Long.MAX_VALUE) 的键
            iterator.seekForPrev(TransactionUtil.enCodeKey(key, Long.MAX_VALUE).getBytes());

            // 如果迭代器无效，说明没有符合条件的记录
            if (!iterator.isValid()) {
                return null;
            }

            // 获取当前键值对
            byte[] gotKey = iterator.key();
            String userKey = TransactionUtil.decodeKey(new String(gotKey));

            // 检查键是否匹配
            if (!userKey.equals(key)) {
                return null;
            }

            // 获取写入记录的值
            byte[] valueBytes = iterator.value();
            Write write = Write.parse(new String(valueBytes));

            // 返回写入记录及其提交时间戳
            long commitTs = TransactionUtil.decodeTs(new String(gotKey));
            return new WriteTs(write, commitTs);
        } finally {
            // 关闭迭代器
            iterator.close();
        }
    }


}
