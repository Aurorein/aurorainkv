package com.aurorain.store;

import com.aurorain.constant.Message;
import com.aurorain.model.Command;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class RocksDBKV implements KV{

    private RocksDB rocksDB;

    public RocksDBKV(String dbPath) {
//        RocksDB.loadLibrary();
        Options options = new Options().setCreateIfMissing(true);
        try {
            rocksDB = RocksDB.open(options, dbPath);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to open RocksDB", e);
        }
    }

    /**
     * 新建或重置键值对
     *
     * @param key
     * @param value
     * @return
     */
    public boolean put(String key, String value) {
        try {
            rocksDB.put(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to put value into RocksDB", e);
        }
    }

    /**
     * key 存在则拼接旧值与 value
     *
     * @param key
     * @param value
     * @return
     */
    public boolean append(String key, String value) {
        try {
            byte[] existingValue = rocksDB.get(key.getBytes(StandardCharsets.UTF_8));
            if (existingValue != null) {
                String newValue = new String(existingValue, StandardCharsets.UTF_8) + value;
                rocksDB.put(key.getBytes(StandardCharsets.UTF_8), newValue.getBytes(StandardCharsets.UTF_8));
            } else {
                rocksDB.put(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to append value in RocksDB", e);
        }
    }

    /**
     * 获取值
     *
     * @param key
     * @return
     */
    public String get(String key) {
        try {
            byte[] value = rocksDB.get(key.getBytes(StandardCharsets.UTF_8));
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to get value from RocksDB", e);
        }
    }

    /**
     * 删除指定键的键值对
     *
     * @param key 要删除的键
     * @return 如果键存在并成功删除，返回 true；否则返回 false
     */
    @Override
    public boolean delete(String key) {
        try {
            // 检查键是否存在
            byte[] value = rocksDB.get(key.getBytes(StandardCharsets.UTF_8));
            if (value == null) {
                return false; // 键不存在
            }
            // 删除键值对
            rocksDB.delete(key.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (RocksDBException e) {
            throw new RuntimeException("Failed to delete key from RocksDB", e);
        }
    }

    /**
     * 对外操作接口
     *
     * @param command
     * @return
     */
    public String opt(Command command) {
        String key = command.getKey();
        String value = command.getValue();
        switch (command.getType()) {
            case PUT:
                return put(key, value) ? Message.OK : Message.KEY_EXIST;
            case APPEND:
                return append(key, value) ? Message.OK : Message.NO_KEY;
            case GET:
                String ret = get(key);
                return ret == null ? Message.NO_KEY : ret;
            default:
                break;
        }
        return Message.OK;
    }

    public void close() {
        if (rocksDB != null) {
            rocksDB.close();
        }
    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return new RocksDBIterator();
    }

    /**
     * RocksDB 迭代器实现
     */
    private class RocksDBIterator implements Iterator<Map.Entry<String, String>> {
        private final RocksIterator iterator;

        public RocksDBIterator() {
            this.iterator = rocksDB.newIterator();
            this.iterator.seekToFirst(); // 定位到第一个键
        }

        @Override
        public boolean hasNext() {
            return iterator.isValid(); // 判断是否还有下一个键值对
        }

        @Override
        public Map.Entry<String, String> next() {
            if (!iterator.isValid()) {
                throw new NoSuchElementException("No more elements in RocksDB");
            }
            // 获取当前键值对
            String key = new String(iterator.key(), StandardCharsets.UTF_8);
            String value = new String(iterator.value(), StandardCharsets.UTF_8);
            iterator.next(); // 移动到下一个键值对
            return Map.entry(key, value); // 返回键值对
        }
    }
}
