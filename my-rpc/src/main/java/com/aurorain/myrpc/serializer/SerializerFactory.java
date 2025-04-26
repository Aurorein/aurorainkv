package com.aurorain.myrpc.serializer;

import com.aurorain.commonmodule.spi.SpiLoader;

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
