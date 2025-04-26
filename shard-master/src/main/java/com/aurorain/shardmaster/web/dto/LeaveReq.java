package com.aurorain.shardmaster.web.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class LeaveReq implements Serializable {
    private List<Integer> gids;
}
