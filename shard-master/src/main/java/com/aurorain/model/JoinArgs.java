package com.aurorain.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class JoinArgs extends Args {

    private Map<Integer, List<Integer>> servers;
}
