package com.bikeassembly.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 简单用户信息 VO，用于工单指派等用户选择场景。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleUserVO {

    private Long userId;
    private String username;
    private String nickname;
    private String roleKey;
}
