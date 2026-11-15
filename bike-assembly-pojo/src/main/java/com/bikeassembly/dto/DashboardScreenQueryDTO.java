package com.bikeassembly.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Read-only screen filters. Calendar days are interpreted in Asia/Shanghai. */
@Data
public class DashboardScreenQueryDTO {
    @NotNull(message = "统计天数不能为空")
    @Min(value = 1, message = "统计天数不能少于1天")
    @Max(value = 30, message = "统计天数不能超过30天")
    private Integer days = 7;

    @Positive(message = "生产单元ID必须大于0")
    private Long workUnitId;
}
