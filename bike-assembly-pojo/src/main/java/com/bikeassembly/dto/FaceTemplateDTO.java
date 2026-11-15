package com.bikeassembly.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

@Data
public class FaceTemplateDTO implements Serializable {
    @NotBlank(message = "人脸图像不能为空")
    @Size(max = 5_600_000, message = "人脸图像过大")
    private String imageBase64;

    @AssertTrue(message = "请先阅读并同意人脸模板处理说明")
    private Boolean consent;
}
