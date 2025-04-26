package com.aurorain.shardkv.transaction;

import com.aurorain.shardkv.constant.CFConstants;
import com.aurorain.shardkv.constant.WriteConstants;
import lombok.Data;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

@Data
public class Scanner {
    private String nextKey;
    private MvccTxn txn;
    private RocksIterator iter;
    private boolean finished;

    public Scanner(String startKey, MvccTxn txn) {
        this.nextKey = startKey;
        this.txn = txn;
        this.iter = txn.reader.iterCf(CFConstants.CfWrite);
        this.finished = false;
    }

    public KVPair next() {
        if(finished) {
            return null;
        }
        String key = this.nextKey;
        iter.seek(TransactionUtil.enCodeKey(key, txn.startTs).getBytes());
        if(!iter.isValid()) {
            finished = true;
            return null;
        }
        String gotKey = new String(iter.key());
        String userKey = TransactionUtil.decodeKey(gotKey);
        if(!userKey.equals(key)) {
            nextKey = userKey;
            return next();
        }
        while(true){
            iter.next();
            if(!iter.isValid()) {
                finished = true;
                break;
            }
            String gotKey1 = new String(iter.key());
            String userKey1 = TransactionUtil.decodeKey(gotKey1);
            if(!userKey1.equals(key)) {
                nextKey = userKey1;
                break;
            }
        }
        String writeVal = new String(iter.value());
        Write write = Write.parse(writeVal);
        write.setWriteKind(WriteConstants.WRITE_KIND_DELETE);
        String value = null;
        try {
            value = this.txn.reader.getCf(CFConstants.CfDefault, TransactionUtil.enCodeKey(key, write.getStartTs()));
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
        return new KVPair(key, value);
    }

    public void close() {
        iter.close();
    }

}
