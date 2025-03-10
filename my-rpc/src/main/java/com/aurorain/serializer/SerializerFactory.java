package com.aurorain.serializer;

import com.aurorain.spi.SpiLoader;

/**
 * 序列化器工厂
 *
 * @author aurorain
 * @version 1.0
 */
public class SerializerFactory {

    static {
        SpiLoader.load(Serializer.class);
    }

    public static Serializer getInstance(String key) {
        return SpiLoader.getInstance(Serializer.class, key);
    }

}
