package com.bikeassembly.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class UserLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

    @ToString.Exclude
    private String password;
}
