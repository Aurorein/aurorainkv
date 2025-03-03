package com.aurorain.model;

import lombok.Data;

import java.io.Serializable;

@Data
public class Reply implements Serializable {
    private boolean isWrongLeader;
}
