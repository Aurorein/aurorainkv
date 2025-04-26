package com.aurorain.shardkv.store;

import com.aurorain.shardkv.common.CommandType;
import com.aurorain.shardkv.constant.Message;
import com.aurorain.shardkv.model.Command;
import io.netty.util.internal.StringUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author aurorain
 * @version 1.0
 */
public class MemKV implements KV {

    private final Map<String, String> kvMap = new HashMap<>();

    /**
     * 新建或重置键值对
     *
     * @param key
     * @param value
     * @return
     */
    @Override
    public boolean put(String key, String value) {
        return kvMap.put(key, value) == null;
    }

    /**
     * key 存在则拼接旧值与 value
     *
     * @param key
     * @param value
     * @return
     */
    @Override
    public boolean append(String key, String value) {
        String pre = kvMap.putIfAbsent(key, value);
        if (pre != null) {
            kvMap.put(key, pre + value);
        }
        return true;
    }

    /**
     * 获取值
     *
     * @param key
     * @return
     */
    @Override
    public String get(String key) {
        return kvMap.get(key);
    }

    @Override
    public boolean delete(String key) {
        String remove = kvMap.remove(key);
        if(StringUtil.isNullOrEmpty(remove)) return false;
        else return true;
    }

    @Override
    public RocksDBReader openReader() {
        return null;
    }

    /**
     * 对外操作接口
     *
     * @param command
     * @return
     */
    @Override
    public String opt(Command command) {
        String key = command.getKey();
        String value = (String)command.getValue();
        switch (command.getType()) {
            case PUT:
                return put(key, value) ? com.aurorain.shardkv.constant.Message.OK : com.aurorain.shardkv.constant.Message.KEY_EXIST;
            case APPEND:
                return append(key, value) ? com.aurorain.shardkv.constant.Message.OK : com.aurorain.shardkv.constant.Message.NO_KEY;
            case GET:
                String ret = get(key);
                return ret == null ? com.aurorain.shardkv.constant.Message.NO_KEY : ret;
            default:
                break;
        }
        return Message.OK;
    }

    @Override
    public void opt(WriteBatch writeBatch) throws Exception {

    }

    @Override
    public Iterator<Map.Entry<String, String>> iterator() {
        return kvMap.entrySet().iterator();
    }

}