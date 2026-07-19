package com.lottery.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserSummary {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private Integer status;
    private LocalDateTime createdAt;
}
