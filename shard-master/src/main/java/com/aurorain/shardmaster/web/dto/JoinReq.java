package com.aurorain.shardmaster.web.dto;


import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class JoinReq implements Serializable {

    private Map<Integer, List<Integer>> servers;
}
