package com.aurorain.service;

import com.aurorain.model.User;

/**
 * @author aurorain
 * @version 1.0
 */
public interface UserService {
    /**
     * 获取用户
     *
     * @param user
     * @return
     */
    User getUser(User user);

    int getNumber();
}
