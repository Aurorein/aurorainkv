package com.aurorain.proxy;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.aurorain.config.RpcConfig;
import com.aurorain.constant.RpcConstant;
import com.aurorain.model.RpcRequest;
import com.aurorain.model.RpcResponse;
import com.aurorain.model.ServiceMetaInfo;
import com.aurorain.registry.Registry;
import com.aurorain.registry.RegistryFactory;
import com.aurorain.serializer.Serializer;
import com.aurorain.serializer.SerializerFactory;
import lombok.NoArgsConstructor;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * 服务代理
 *
 * @author aurorain
 * @version 1.0
 */
@NoArgsConstructor
public class ServiceProxy implements InvocationHandler {

    private boolean callAll = false;

    private int chosen = -1;

    private String version = "1.0";

    public ServiceProxy(boolean b, int except) {
        callAll = b;
        chosen = except;
    }

    public ServiceProxy(int i, String v) {
        chosen = i;
        version = v;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //指定序列化器
        final Serializer serializer = SerializerFactory.getInstance(RpcConfig.getRpcConfig().getSerializer());

        // 发请求
        String serviceName = method.getDeclaringClass().getName();
        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName(serviceName)
                .methodName(method.getName())
                .parameterTypes(method.getParameterTypes())
                .args(args)
                .build();

        try {
            byte[] bodyBytes = serializer.serialize(rpcRequest);

            // 从注册中心获取服务提供者请求地址
            RpcConfig rpcConfig = RpcConfig.getRpcConfig();
            Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceVersion(version);
            Map<Integer, ServiceMetaInfo> map = registry.serviceDiscovery(serviceMetaInfo.getServiceKey());
            if (CollUtil.isEmpty(map)) {
                throw new RuntimeException("no service address");
            }
            // todo:修改选择
            ServiceMetaInfo selectedServiceMetaInfo;
            if (chosen != -1) {
                selectedServiceMetaInfo = map.get(chosen);
            } else {
                selectedServiceMetaInfo = map.get(0);
            }

            // 发送请求
            try (HttpResponse httpResponse = HttpRequest.post(selectedServiceMetaInfo.getServiceAddress())
                    .body(bodyBytes)
                    .execute()) {
                byte[] result = httpResponse.bodyBytes();
                // 反序列化
                RpcResponse rpcResponse = serializer.deserialize(result, RpcResponse.class);
                return rpcResponse.getData();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}
