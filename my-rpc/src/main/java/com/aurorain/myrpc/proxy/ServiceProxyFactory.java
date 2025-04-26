package com.aurorain.myrpc.proxy;


import com.aurorain.myrpc.config.RpcConfig;

import java.lang.reflect.Proxy;

/**
 * 服务代理工厂
 *
 * @author aurorain
 * @version 1.0
 */
public class ServiceProxyFactory {
    /**
     * 获取代理对象
     *
     * @param serviceClass
     * @param <T>
     * @return
     */
    public static <T> T getProxy(Class<T> serviceClass) {
        if (RpcConfig.getRpcConfig().isMock()) {
            return getMockProxy(serviceClass);
        }

        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new ServiceProxy()
        );
    }

    public static <T> T getProxy(Class<T> serviceClass, boolean callAll, int except) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new ServiceProxy(callAll, except)
        );
    }

    public static <T> T getProxy(Class<T> serviceClass, int chosen, String version) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new ServiceProxy(chosen, version)
        );
    }

    public static <T> T getMockProxy(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new MockServiceProxy()
        );
    }
}
