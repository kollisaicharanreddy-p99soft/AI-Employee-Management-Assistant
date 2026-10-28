package com.employee.management.controller;

import com.employee.management.dto.DepartmentCountDto;
import com.employee.management.dto.StatusCountDto;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.service.AnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@DisplayName("AnalyticsController — web layer tests")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("GET /api/v1/analytics/employees/count returns total count")
    void getTotalCount() throws Exception {
        when(analyticsService.getTotalEmployeeCount()).thenReturn(42L);

        mockMvc.perform(get("/api/v1/analytics/employees/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(42));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/employees/count-by-status returns status buckets")
    void getCountByStatus() throws Exception {
        when(analyticsService.getCountByStatus()).thenReturn(List.of(
                StatusCountDto.builder().status(EmployeeStatus.ACTIVE).count(3).build(),
                StatusCountDto.builder().status(EmployeeStatus.INACTIVE).count(0).build(),
                StatusCountDto.builder().status(EmployeeStatus.ON_LEAVE).count(1).build(),
                StatusCountDto.builder().status(EmployeeStatus.TERMINATED).count(1).build()
        ));

        mockMvc.perform(get("/api/v1/analytics/employees/count-by-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].count").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/employees/count-by-department returns department buckets")
    void getCountByDepartment() throws Exception {
        when(analyticsService.getCountByDepartment()).thenReturn(List.of(
                DepartmentCountDto.builder().departmentName("Engineering").count(3).build(),
                DepartmentCountDto.builder().departmentName("Finance").count(2).build()
        ));

        mockMvc.perform(get("/api/v1/analytics/employees/count-by-department"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].departmentName").value("Engineering"))
                .andExpect(jsonPath("$.data[0].count").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/workforce-summary returns full summary")
    void getWorkforceSummary() throws Exception {
        WorkforceSummaryDto summary = WorkforceSummaryDto.builder()
                .totalEmployees(5)
                .totalDepartments(2)
                .countByStatus(List.of(
                        StatusCountDto.builder().status(EmployeeStatus.ACTIVE).count(3).build()
                ))
                .countByDepartment(List.of(
                        DepartmentCountDto.builder().departmentName("Engineering").count(3).build()
                ))
                .build();

        when(analyticsService.getWorkforceSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/workforce-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalEmployees").value(5))
                .andExpect(jsonPath("$.data.totalDepartments").value(2))
                .andExpect(jsonPath("$.data.countByStatus.length()").value(1))
                .andExpect(jsonPath("$.data.countByDepartment.length()").value(1));
    }
}
