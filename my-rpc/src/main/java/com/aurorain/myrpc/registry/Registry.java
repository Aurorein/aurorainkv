package com.aurorain.myrpc.registry;

import com.aurorain.myrpc.config.RegistryConfig;
import com.aurorain.commonmodule.model.ServiceMetaInfo;

import java.util.Map;

/**
 * 注册中心
 *
 * @author aurorain
 * @version 1.0
 */
public interface Registry {

    /**
     * 服务中心初始化
     *
     * @param registryConfig
     */
    void init(RegistryConfig registryConfig);

    /**
     * 服务注册（服务端）
     *
     * @param serviceMetaInfo
     * @throws Exception
     */
    void register(ServiceMetaInfo serviceMetaInfo) throws Exception;

    /**
     * 注销服务（服务端）
     *
     * @param serviceMetaInfo
     */
    void unRegister(ServiceMetaInfo serviceMetaInfo);

    /**
     * 发现某服务的所有节点（消费端）
     *
     * @param serviceKey
     * @return
     */
    Map<Integer, ServiceMetaInfo> serviceDiscovery(String serviceKey);

    /**
     * 服务销毁
     */
    void destroy();

    /**
     * 心跳机制（服务端）
     */
    void heartbeat();

    /**
     * 服务监听（消费端）
     *
     * @param serviceNodeKey
     */
    void watch(String serviceNodeKey);



}
