package com.bikeassembly.service;

import com.bikeassembly.vo.DashboardVO;

/**
 * 生产运营看板服务。
 */
public interface DashboardService {

    /**
     * 获取看板聚合数据。
     */
    DashboardVO overview();
}
