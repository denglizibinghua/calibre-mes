package com.bikeassembly.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Typed, database-backed production screen; rates are null when no samples exist. */
@Data
public class DashboardScreenVO {
    private LocalDateTime generatedAt;
    private LocalDate startDate;
    private LocalDate endDate;
    private String dataScope;
    private Summary summary;
    private Equipment equipment;
    private Quality quality;
    private List<Trend> trend;
    private List<ProcessLoad> processes;
    private List<WorkOrder> workOrders;
    private List<Alert> alerts;
    private List<Defect> defects;
    private List<WorkUnit> workUnits;

    @Data
    public static class Summary {
        private long activeWorkOrders;
        private long todayCompletedWorkOrders;
        private long overdueWorkOrders;
        private long pendingInspections;
        private long openAlerts;
    }

    @Data
    public static class Equipment {
        private long available;
        private long fault;
        private long disabled;
        private BigDecimal availabilityRate;
    }

    @Data
    public static class Quality {
        private long inspected;
        private long passed;
        private BigDecimal firstPassRate;
    }

    @Data
    public static class Trend {
        private LocalDate date;
        private long planned;
        private long completed;
        private long inspected;
        private long passed;
    }

    @Data
    public static class ProcessLoad {
        private Long processId;
        private String processName;
        private long total;
        private long running;
        private long waiting;
        private long abnormal;
        private long completed;
        private long queued;
    }

    @Data
    public static class WorkOrder {
        private Long workOrderId;
        private String workOrderNo;
        private String itemName;
        private String unitName;
        private BigDecimal planQty;
        private String unit;
        private String status;
        private LocalDateTime planEndTime;
        private long totalSteps;
        private long completedSteps;
        private boolean overdue;
    }

    @Data
    public static class Alert {
        private Long alertId;
        private String alertNo;
        private String alertType;
        private String alertLevel;
        private String status;
        private String unitName;
        private String equipmentName;
        private LocalDateTime createTime;
    }

    @Data
    public static class Defect {
        private String name;
        private long value;
    }

    @Data
    public static class WorkUnit {
        private Long unitId;
        private String unitName;
        private String unitType;
    }
}
