package com.bikeassembly.service;

import com.bikeassembly.dto.DashboardScreenQueryDTO;
import com.bikeassembly.vo.DashboardScreenVO;

public interface DashboardScreenService {
    DashboardScreenVO screen(DashboardScreenQueryDTO query);
}
