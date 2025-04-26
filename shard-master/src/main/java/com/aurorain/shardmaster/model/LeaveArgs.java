package com.aurorain.shardmaster.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class LeaveArgs extends Args {

    private List<Integer> gids;
}
