package com.aurorain.shardkv.model.dto;

import lombok.Data;
import java.util.List;

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
     * 事务执行的处理，用在事务接口中
     */
    private int txnAction = 0;

    private List<KeyError> keyErrors;

    /**
     * 结果值
     */
    private T value;

    public CommandResponse() {

    }

    public CommandResponse(boolean success, String err) {
        this.success = success;
        this.err = err;
    }

    public CommandResponse(boolean success, String err, int txnConstant) {
        this.success = success;
        this.err = err;
        this.txnAction = txnConstant;
    }
}
