package com.aurorain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
@Data
@AllArgsConstructor
public class ShardIndexAndTerm implements Serializable {

    private int index;

    private int term;

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        ShardIndexAndTerm shardIndexAndTerm = (ShardIndexAndTerm) obj;
        return shardIndexAndTerm.index == this.index && shardIndexAndTerm.term == this.term;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(index);
        result = 31 * result + Integer.hashCode(term);
        return result;
    }

}
