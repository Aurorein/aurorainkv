package com.aurorain.raft.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 投票响应
 *
 * @author aurorain
 * @version 1.0
 */
@Data
public class RequestVoteResponse implements Serializable {

    /**
     * 接收方的 term
     */
    private int term;

    /**
     * 是否投票
     */
    private boolean voteGranted;

}
