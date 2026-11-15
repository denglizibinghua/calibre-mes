package com.bikeassembly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 人脸登录请求。imageBase64 只在请求内存中处理，不落盘。
 */
@Data
public class FaceLoginDTO implements Serializable {
    @Size(max = 80, message = "账号长度不能超过 80 个字符")
    private String username;

    @NotBlank(message = "人脸图像不能为空")
    @Size(max = 5_600_000, message = "人脸图像过大")
    private String imageBase64;
}
