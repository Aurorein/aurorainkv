package com.aurorain.provider;

import com.aurorain.config.RegistryConfig;
import com.aurorain.config.RpcConfig;
import com.aurorain.model.ServiceMetaInfo;
import com.aurorain.registry.LocalRegistry;
import com.aurorain.registry.Registry;
import com.aurorain.registry.RegistryFactory;
import com.aurorain.server.HttpServer;
import com.aurorain.server.VertxHttpServer;
import com.aurorain.service.UserService;

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
