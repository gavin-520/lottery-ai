package com.lottery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String nickname;

    @Pattern(regexp = "ADMIN|ANALYST|USER")
    private String role = "USER";

    private Integer status = 1;
}
