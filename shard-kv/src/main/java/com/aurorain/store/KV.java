package com.aurorain.store;

import com.aurorain.model.Command;

import java.util.Iterator;
import java.util.Map;

public interface KV {
    boolean put(String key, String value);

    boolean append(String key, String value);

    String get(String key);

    boolean delete(String key);

    String opt(Command command);

    Iterator<Map.Entry<String, String>> iterator();
}
