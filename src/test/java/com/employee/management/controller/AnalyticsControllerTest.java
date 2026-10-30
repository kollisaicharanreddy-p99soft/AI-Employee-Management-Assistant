package com.employee.management.controller;

import com.employee.management.dto.DepartmentCountDto;
import com.employee.management.dto.DepartmentDetailDto;
import com.employee.management.dto.DepartmentReportDataDto;
import com.employee.management.dto.ReportRequest;
import com.employee.management.dto.ReportResponse;
import com.employee.management.dto.ReportType;
import com.employee.management.dto.StatusCountDto;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.model.EmployeeStatus;
import com.employee.management.service.AnalyticsService;
import com.employee.management.service.WorkforceReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@DisplayName("AnalyticsController — web layer tests")
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalyticsService analyticsService;

    @MockBean
    private WorkforceReportService workforceReportService;

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

    @Test
    @DisplayName("POST /api/v1/analytics/reports generates report successfully")
    void generateReport_success() throws Exception {
        ReportResponse<DepartmentReportDataDto> mockResponse = ReportResponse.<DepartmentReportDataDto>builder()
                .reportType(ReportType.DEPARTMENT_REPORT)
                .generatedAt(LocalDateTime.now())
                .summary("The organization has 1 department.")
                .data(DepartmentReportDataDto.builder()
                        .totalDepartments(1)
                        .totalEmployees(2)
                        .departments(List.of(
                                DepartmentDetailDto.builder()
                                        .id(1L)
                                        .name("Engineering")
                                        .description("Software")
                                        .employeeCount(2)
                                        .build()
                        ))
                        .build())
                .build();

        when(workforceReportService.generateReport(any(ReportRequest.class)))
                .thenAnswer(invocation -> mockResponse);

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.DEPARTMENT_REPORT)
                .departmentName("Engineering")
                .build();

        mockMvc.perform(post("/api/v1/analytics/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Report generated successfully"))
                .andExpect(jsonPath("$.data.reportType").value("DEPARTMENT_REPORT"))
                .andExpect(jsonPath("$.data.summary").value("The organization has 1 department."))
                .andExpect(jsonPath("$.data.data.totalDepartments").value(1))
                .andExpect(jsonPath("$.data.data.departments[0].name").value("Engineering"));
    }

    @Test
    @DisplayName("POST /api/v1/analytics/reports with invalid report type returns 400")
    void generateReport_invalidReportType() throws Exception {
        String invalidPayload = """
                {
                    "reportType": "INVALID_TYPE"
                }
                """;

        mockMvc.perform(post("/api/v1/analytics/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/analytics/reports with missing report type returns 400")
    void generateReport_missingReportType() throws Exception {
        String missingPayload = """
                {
                    "departmentName": "Finance"
                }
                """;

        mockMvc.perform(post("/api/v1/analytics/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
