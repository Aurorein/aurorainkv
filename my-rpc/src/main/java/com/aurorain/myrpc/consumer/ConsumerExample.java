package com.aurorain.myrpc.consumer;

import com.aurorain.commonmodule.model.User;
import com.aurorain.myrpc.proxy.ServiceProxyFactory;
import com.aurorain.myrpc.service.UserService;

/**
 * @author aurorain
 * @version 1.0
 */
public class ConsumerExample {
    public static void main(String[] args) {
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);
        User user = new User();
        user.setName("aurorain");
        User newUser = userService.getUser(user);
        if (newUser != null) {
            System.out.println(newUser.getName());
        } else {
            System.out.println("error");
        }
        System.out.println(userService.getNumber());
    }
}
