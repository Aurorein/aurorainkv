package com.aurorain.shardkv.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public
class RoundResult {
    private int round;
    private List<VerifiedOperation> operations;
    private List<ClientResult> clientErrors;
    private boolean valid;
}
