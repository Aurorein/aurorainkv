package com.aurorain.shardkv.web.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public
class ConsistencyTestResult {
    private int clientCount;
    private int serverCount;
    private List<RoundResult> rounds;
    private boolean allRoundsValid;
}
