package com.bikeassembly.dto;

import com.bikeassembly.validation.OnCreate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;
import java.time.LocalDateTime;

import java.io.Serializable;

@Data
public class SysUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;
    /** 登录账号 */
    @NotBlank(message = "用户名不能为空", groups = OnCreate.class)
    private String username;
    /** 密码密文 */
    @NotBlank(message = "密码不能为空", groups = OnCreate.class)
    @Size(min = 6, max = 128, message = "密码长度6-128位")
    @ToString.Exclude
    private String password;
    /** 用户姓名 */
    private String nickname;
    /** 手机号 */
    private String phone;
    /** 角色ID */
    private Long roleId;
    /** 状态 */
    @Size(max = 1, message = "状态长度不能超过1")
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
