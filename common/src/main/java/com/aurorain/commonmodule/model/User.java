package com.aurorain.commonmodule.model;

import java.io.Serializable;

/**
 * @author aurorain
 * @version 1.0
 */
public class User implements Serializable {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
