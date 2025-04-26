package com.aurorain.shardkv.store;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.rocksdb.*;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RocksDBReader {
    private TransactionDB db;

    private Transaction txn;

    private List<ColumnFamilyHandle> cfHandles;

    public String getCf(String cf, String key) throws RocksDBException {
        // 从事务中读取数据
        return RocksDBKV.get(cfHandles, txn, cf, key);
    }

    public RocksIterator iterCf(String cf) {
        ColumnFamilyHandle cfHandle = RocksDBKV.getColumnFamilyHandle(cfHandles, cf);
        ReadOptions readOptions = new ReadOptions().setSnapshot(txn.getSnapshot());
        RocksIterator iter = txn.getIterator(readOptions, cfHandle);
        return iter;
    }

    /**
     * 回滚事务并释放资源
     */
    public void rollback() throws RocksDBException {
        txn.rollback();
        close();
    }

    /**
     * 释放资源
     */
    public void close() {
        txn.close();
    }
}
