package com.aurorain.model.dto.dto;

import lombok.Data;

import java.io.Serializable;


@Data
public class ShardCommandResponse implements Serializable {

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
    private Object value;

}
