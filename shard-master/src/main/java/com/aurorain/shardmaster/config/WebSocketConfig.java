package com.aurorain.shardmaster.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 设置应用目的地的前缀（即客户端发送的消息会带有/app前缀）
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 配置 WebSocket 连接的端点，并启用 SockJS 支持
        registry.addEndpoint("/ws")
                .setAllowedOrigins("http://localhost:5173")  // 允许跨域的源（前端 URL）
                .withSockJS();
    }
}
