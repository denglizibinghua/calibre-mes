package com.bikeassembly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ScanResolveDTO {
    @NotBlank(message = "扫码内容不能为空")
    @Size(max = 256, message = "扫码内容不能超过256个字符")
    private String content;
}
