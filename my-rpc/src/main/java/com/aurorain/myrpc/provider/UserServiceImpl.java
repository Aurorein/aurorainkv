package com.aurorain.myrpc.provider;

import com.aurorain.commonmodule.model.User;
import com.aurorain.myrpc.service.UserService;

/**
 * @author aurorain
 * @version 1.0
 */
public class UserServiceImpl implements UserService {
    @Override
    public User getUser(User user) {
        System.out.println(user.getName());
        return user;
    }

    @Override
    public int getNumber() {
        return 1;
    }
}
