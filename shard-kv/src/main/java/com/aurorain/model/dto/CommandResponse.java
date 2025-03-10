package com.aurorain.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
public class CommandResponse<T> implements Serializable {

    /**
     * 成功标识
     */
    private boolean success;

    /**
     * 错误标识
     */
    private String err;

    /**
     * 结果值
     */
    private T value;

}
