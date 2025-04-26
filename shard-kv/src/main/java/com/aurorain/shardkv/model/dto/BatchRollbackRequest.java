package com.aurorain.shardkv.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class BatchRollbackRequest extends CommandRequest implements Serializable {
    private long startTs;
    private List<String> keys;
}
