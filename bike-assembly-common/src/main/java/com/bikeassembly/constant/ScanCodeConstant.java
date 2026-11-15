package com.bikeassembly.constant;

/** 工单码仅定位业务对象，不携带访问凭据。 */
public final class ScanCodeConstant {
    private ScanCodeConstant() {}
    public static final String PREFIX = "BIKE-MES:1:WORK_ORDER:";
    public static final String WORK_ORDER = "WORK_ORDER";
    public static final String WORK_ORDER_DETAIL = "WORK_ORDER_DETAIL";
    public static final String WORK_PROCESS = "WORK_PROCESS";
    public static final String INSPECTION_PENDING = "INSPECTION_PENDING";
    public static final int INVALID_CODE = 46001;
    public static final int UNSUPPORTED_CODE = 46002;
    public static final int ORDER_NOT_FOUND = 46003;
    public static final int NO_PENDING_INSPECTION = 46004;
    public static final long MAX_SAFE_ID = 9007199254740991L;
}
