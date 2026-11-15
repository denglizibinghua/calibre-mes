package com.bikeassembly.mapper;

import com.bikeassembly.vo.DashboardScreenVO.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DashboardScreenMapper {
    Summary summary(@Param("unitId") Long unitId, @Param("userId") Long userId,
                    @Param("today") LocalDateTime today, @Param("now") LocalDateTime now);
    Equipment equipment(@Param("unitId") Long unitId, @Param("userId") Long userId);
    Quality quality(@Param("unitId") Long unitId, @Param("userId") Long userId,
                    @Param("start") LocalDateTime start, @Param("now") LocalDateTime now);
    List<Trend> plannedTrend(@Param("unitId") Long unitId, @Param("userId") Long userId,
                            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    List<Trend> completedTrend(@Param("unitId") Long unitId, @Param("userId") Long userId,
                              @Param("start") LocalDateTime start, @Param("now") LocalDateTime now);
    List<Trend> qualityTrend(@Param("unitId") Long unitId, @Param("userId") Long userId,
                            @Param("start") LocalDateTime start, @Param("now") LocalDateTime now);
    List<ProcessLoad> processes(@Param("unitId") Long unitId, @Param("userId") Long userId);
    List<WorkOrder> workOrders(@Param("unitId") Long unitId, @Param("userId") Long userId,
                              @Param("start") LocalDateTime start, @Param("now") LocalDateTime now);
    List<Alert> alerts(@Param("unitId") Long unitId, @Param("userId") Long userId,
                       @Param("now") LocalDateTime now);
    List<Defect> defects(@Param("unitId") Long unitId, @Param("userId") Long userId,
                        @Param("start") LocalDateTime start, @Param("now") LocalDateTime now);
    List<WorkUnit> workUnits(@Param("userId") Long userId);
}
