package com.bikeassembly.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI 助手的只读数据检索。所有条件都由服务层传入，AI 本身不会接触 SQL。
 */
@Mapper
public interface AiMesDataMapper {

    List<Map<String, Object>> qualityByProcess(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end,
                                                @Param("userId") Long userId,
                                                @Param("unitIds") List<Long> unitIds);

    List<Map<String, Object>> inventorySummary(@Param("userId") Long userId,
                                                @Param("unitIds") List<Long> unitIds);

    List<Map<String, Object>> repairSummary(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("userId") Long userId,
                                             @Param("unitIds") List<Long> unitIds);

    List<Map<String, Object>> andonSummary(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end,
                                            @Param("userId") Long userId,
                                            @Param("unitIds") List<Long> unitIds);
}
