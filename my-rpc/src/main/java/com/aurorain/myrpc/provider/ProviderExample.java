package com.aurorain.myrpc.provider;

import com.aurorain.myrpc.config.RegistryConfig;
import com.aurorain.myrpc.config.RpcConfig;
import com.aurorain.commonmodule.model.ServiceMetaInfo;
import com.aurorain.myrpc.registry.LocalRegistry;
import com.aurorain.myrpc.registry.Registry;
import com.aurorain.myrpc.registry.RegistryFactory;
import com.aurorain.myrpc.server.HttpServer;
import com.aurorain.myrpc.server.VertxHttpServer;
import com.aurorain.myrpc.service.UserService;

/**
 * @author aurorain
 * @version 1.0
 */
public class ProviderExample {
    public static void main(String[] args) {
        RpcConfig rpcConfig = RpcConfig.getRpcConfig();

        String serviceName = UserService.class.getName();

        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(serviceName);
        serviceMetaInfo.setServiceVersion("1.0");
        serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
        serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
        try {
            registry.register(serviceMetaInfo);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LocalRegistry.register(serviceName, UserServiceImpl.class);

        // http 服务器的端口号和服务注册的端口号一致
        HttpServer httpServer = new VertxHttpServer();
        httpServer.doStart(rpcConfig.getServerPort());
    }
}
