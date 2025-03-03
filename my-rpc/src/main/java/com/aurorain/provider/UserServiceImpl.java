package com.aurorain.provider;

import com.aurorain.model.User;
import com.aurorain.service.UserService;

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
