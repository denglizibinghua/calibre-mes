package com.bikeassembly.service.impl;

import com.bikeassembly.context.BaseContext;
import com.bikeassembly.dto.DashboardScreenQueryDTO;
import com.bikeassembly.mapper.DashboardScreenMapper;
import com.bikeassembly.service.DashboardScreenService;
import com.bikeassembly.vo.DashboardScreenVO;
import com.bikeassembly.vo.DashboardScreenVO.Trend;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardScreenServiceImpl implements DashboardScreenService {
    private final DashboardScreenMapper mapper;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    @Transactional(readOnly = true)
    public DashboardScreenVO screen(DashboardScreenQueryDTO query) {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDate today = now.toLocalDate();
        LocalDate start = today.minusDays(query.getDays() - 1L);
        // Missing identity must never turn a restricted query into an unfiltered query.
        boolean all = BaseContext.getCurrentId() != null && "ALL".equals(BaseContext.getCurrentDataScope());
        Long userId = all ? null : (BaseContext.getCurrentId() == null ? -1L : BaseContext.getCurrentId());
        Long unitId = query.getWorkUnitId();
        DashboardScreenVO result = new DashboardScreenVO();
        result.setGeneratedAt(now);
        result.setStartDate(start);
        result.setEndDate(today);
        result.setDataScope(all ? "ALL" : "SELF");
        result.setSummary(mapper.summary(unitId, userId, today.atStartOfDay(), now));
        var equipment = mapper.equipment(unitId, userId);
        equipment.setAvailabilityRate(rate(equipment.getAvailable(), equipment.getAvailable() + equipment.getFault()));
        result.setEquipment(equipment);
        var quality = mapper.quality(unitId, userId, start.atStartOfDay(), now);
        quality.setFirstPassRate(rate(quality.getPassed(), quality.getInspected()));
        result.setQuality(quality);
        result.setTrend(mergeTrend(start, today,
                mapper.plannedTrend(unitId, userId, start.atStartOfDay(), today.plusDays(1).atStartOfDay()),
                mapper.completedTrend(unitId, userId, start.atStartOfDay(), now),
                mapper.qualityTrend(unitId, userId, start.atStartOfDay(), now)));
        result.setProcesses(mapper.processes(unitId, userId));
        result.setWorkOrders(mapper.workOrders(unitId, userId, start.atStartOfDay(), now));
        result.setAlerts(mapper.alerts(unitId, userId, now));
        result.setDefects(mapper.defects(unitId, userId, start.atStartOfDay(), now));
        result.setWorkUnits(mapper.workUnits(userId));
        return result;
    }

    private BigDecimal rate(long numerator, long denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private List<Trend> mergeTrend(LocalDate start, LocalDate end, List<Trend> planned,
                                  List<Trend> completed, List<Trend> quality) {
        Map<LocalDate, Trend> p = byDate(planned);
        Map<LocalDate, Trend> c = byDate(completed);
        Map<LocalDate, Trend> q = byDate(quality);
        List<Trend> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            Trend point = new Trend();
            point.setDate(date);
            if (p.containsKey(date)) point.setPlanned(p.get(date).getPlanned());
            if (c.containsKey(date)) point.setCompleted(c.get(date).getCompleted());
            if (q.containsKey(date)) {
                point.setInspected(q.get(date).getInspected());
                point.setPassed(q.get(date).getPassed());
            }
            result.add(point);
        }
        return result;
    }

    private Map<LocalDate, Trend> byDate(List<Trend> rows) {
        return rows.stream().collect(Collectors.toMap(Trend::getDate, Function.identity()));
    }
}
