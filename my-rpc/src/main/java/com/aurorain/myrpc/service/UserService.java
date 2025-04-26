package com.aurorain.myrpc.service;

import com.aurorain.commonmodule.model.User;

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
