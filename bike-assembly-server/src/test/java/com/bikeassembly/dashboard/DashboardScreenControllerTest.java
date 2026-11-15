package com.bikeassembly.dashboard;

import com.bikeassembly.controller.admin.DashboardScreenController;
import com.bikeassembly.service.DashboardScreenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DashboardScreenControllerTest {
    private final DashboardScreenService service = mock(DashboardScreenService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new DashboardScreenController(service)).build();

    @Test
    void invalidDaysReturnBadRequestWithoutRunningQueries() throws Exception {
        mvc.perform(get("/admin/dashboard/screen").param("days", "31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("统计天数不能超过30天"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedIntegerDoesNotLeakFrameworkBindingDetails() throws Exception {
        mvc.perform(get("/admin/dashboard/screen").param("days", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("统计天数必须是整数"));
        mvc.perform(get("/admin/dashboard/screen").param("workUnitId", "not-an-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("生产单元ID必须是整数"));
        verifyNoInteractions(service);
    }
}
