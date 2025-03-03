package com.aurorain.config;

import lombok.Data;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class RegistryConfig {

    /**
     * 注册中心类别
     */
    private String Registry = "etcd";

    /**
     * 注册中心地址
     */
    private String address = "http://localhost:2380";

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 超时时间
     */
    private Long timeout = 10000L;

}
