package com.aurorain.consumer;

import com.aurorain.model.User;
import com.aurorain.proxy.ServiceProxyFactory;
import com.aurorain.service.UserService;

/**
 * @author aurorain
 * @version 1.0
 */
public class ConsumerExample {
    public static void main(String[] args) {
        UserService userService = ServiceProxyFactory.getProxy(UserService.class);
        User user = new User();
        user.setName("sakame");
        User newUser = userService.getUser(user);
        if (newUser != null) {
            System.out.println(newUser.getName());
        } else {
            System.out.println("error");
        }
        System.out.println(userService.getNumber());
    }
}
