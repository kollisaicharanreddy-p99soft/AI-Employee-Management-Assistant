package com.employee.management.service;

import com.employee.management.dto.DepartmentCountDto;
import com.employee.management.dto.DepartmentDetailDto;
import com.employee.management.dto.DepartmentReportDataDto;
import com.employee.management.dto.ReportRequest;
import com.employee.management.dto.ReportResponse;
import com.employee.management.dto.ReportType;
import com.employee.management.dto.StatusDistributionDto;
import com.employee.management.dto.StatusReportDataDto;
import com.employee.management.dto.WorkforceSummaryDto;
import com.employee.management.model.EmployeeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkforceReportService — unit tests with mock ChatModel")
class WorkforceReportServiceTest {

    @Mock
    private AnalyticsService analyticsService;

    @Mock
    private ChatModel chatModel;

    private WorkforceReportService workforceReportService;

    @BeforeEach
    void setUp() {
        workforceReportService = new WorkforceReportService(analyticsService, chatModel);
    }

    @Test
    @DisplayName("generateReport throws IllegalArgumentException when request or reportType is null")
    void generateReport_nullRequest() {
        assertThatThrownBy(() -> workforceReportService.generateReport(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Report type is required");

        ReportRequest emptyRequest = new ReportRequest();
        assertThatThrownBy(() -> workforceReportService.generateReport(emptyRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Report type is required");
    }

    @Test
    @DisplayName("generateReport for WORKFORCE_OVERVIEW uses LLM summary when valid")
    void generateReport_workforceOverview_aiSummary() {
        WorkforceSummaryDto summaryDto = WorkforceSummaryDto.builder()
                .totalEmployees(10)
                .totalDepartments(2)
                .countByDepartment(List.of(new DepartmentCountDto("Engineering", 6), new DepartmentCountDto("HR", 4)))
                .build();
        when(analyticsService.getWorkforceSummary()).thenReturn(summaryDto);

        String expectedAiSummary = "The workforce comprises 10 total staff across 2 departments.";
        org.springframework.ai.chat.model.ChatResponse mockAiResponse =
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage(expectedAiSummary))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse);

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.WORKFORCE_OVERVIEW)
                .build();

        ReportResponse<?> response = workforceReportService.generateReport(request);

        assertThat(response.getReportType()).isEqualTo(ReportType.WORKFORCE_OVERVIEW);
        assertThat(response.getSummary()).isEqualTo(expectedAiSummary);
        assertThat(response.getData()).isEqualTo(summaryDto);
    }

    @Test
    @DisplayName("generateReport for WORKFORCE_OVERVIEW uses deterministic fallback when LLM fails")
    void generateReport_workforceOverview_fallbackOnLlmFailure() {
        WorkforceSummaryDto summaryDto = WorkforceSummaryDto.builder()
                .totalEmployees(5)
                .totalDepartments(2)
                .countByDepartment(List.of(new DepartmentCountDto("Engineering", 3), new DepartmentCountDto("Finance", 2)))
                .build();
        when(analyticsService.getWorkforceSummary()).thenReturn(summaryDto);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM timeout"));

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.WORKFORCE_OVERVIEW)
                .build();

        ReportResponse<?> response = workforceReportService.generateReport(request);

        assertThat(response.getReportType()).isEqualTo(ReportType.WORKFORCE_OVERVIEW);
        assertThat(response.getSummary()).contains("5 staff members across 2 departments");
        assertThat(response.getData()).isEqualTo(summaryDto);
    }

    @Test
    @DisplayName("generateReport for DEPARTMENT_REPORT produces structured data and fallback when empty")
    void generateReport_departmentReport_empty() {
        when(analyticsService.getDepartmentDetails("Unknown")).thenReturn(List.of());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM unavailable"));

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.DEPARTMENT_REPORT)
                .departmentName("Unknown")
                .build();

        ReportResponse<?> response = workforceReportService.generateReport(request);

        assertThat(response.getReportType()).isEqualTo(ReportType.DEPARTMENT_REPORT);
        DepartmentReportDataDto data = (DepartmentReportDataDto) response.getData();
        assertThat(data.getTotalDepartments()).isZero();
        assertThat(data.getTotalEmployees()).isZero();
        assertThat(response.getSummary()).isEqualTo("No department records found matching 'Unknown'.");
    }

    @Test
    @DisplayName("generateReport for DEPARTMENT_REPORT populates verified departments and headcounts")
    void generateReport_departmentReport_populated() {
        List<DepartmentDetailDto> depts = List.of(
                DepartmentDetailDto.builder().id(1L).name("Engineering").description("Eng").employeeCount(4).build(),
                DepartmentDetailDto.builder().id(2L).name("Finance").description("Fin").employeeCount(2).build()
        );
        when(analyticsService.getDepartmentDetails(null)).thenReturn(depts);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM unavailable"));

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.DEPARTMENT_REPORT)
                .build();

        ReportResponse<?> response = workforceReportService.generateReport(request);

        DepartmentReportDataDto data = (DepartmentReportDataDto) response.getData();
        assertThat(data.getTotalDepartments()).isEqualTo(2);
        assertThat(data.getTotalEmployees()).isEqualTo(6);
        assertThat(response.getSummary()).contains("2 department(s) with a total of 6 employees");
    }

    @Test
    @DisplayName("generateReport for EMPLOYEE_STATUS_REPORT handles percentages and zero division")
    void generateReport_employeeStatusReport_zeroDivision() {
        when(analyticsService.getTotalEmployeeCount()).thenReturn(0L);
        when(analyticsService.getStatusDistribution()).thenReturn(List.of(
                new StatusDistributionDto(EmployeeStatus.ACTIVE, 0, 0.0),
                new StatusDistributionDto(EmployeeStatus.INACTIVE, 0, 0.0),
                new StatusDistributionDto(EmployeeStatus.ON_LEAVE, 0, 0.0),
                new StatusDistributionDto(EmployeeStatus.TERMINATED, 0, 0.0)
        ));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM offline"));

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.EMPLOYEE_STATUS_REPORT)
                .build();

        ReportResponse<?> response = workforceReportService.generateReport(request);

        assertThat(response.getReportType()).isEqualTo(ReportType.EMPLOYEE_STATUS_REPORT);
        StatusReportDataDto data = (StatusReportDataDto) response.getData();
        assertThat(data.getTotalEmployees()).isZero();
        assertThat(response.getSummary()).isEqualTo("The organization has 0 total employees. All employment status counts are currently at zero.");
    }

    @Test
    @DisplayName("generateReport for EMPLOYEE_STATUS_REPORT formats status percentages accurately")
    void generateReport_employeeStatusReport_populated() {
        when(analyticsService.getTotalEmployeeCount()).thenReturn(4L);
        when(analyticsService.getStatusDistribution()).thenReturn(List.of(
                new StatusDistributionDto(EmployeeStatus.ACTIVE, 3, 75.0),
                new StatusDistributionDto(EmployeeStatus.INACTIVE, 0, 0.0),
                new StatusDistributionDto(EmployeeStatus.ON_LEAVE, 1, 25.0),
                new StatusDistributionDto(EmployeeStatus.TERMINATED, 0, 0.0)
        ));
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM offline"));

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.EMPLOYEE_STATUS_REPORT)
                .build();

        ReportResponse<?> response = workforceReportService.generateReport(request);

        StatusReportDataDto data = (StatusReportDataDto) response.getData();
        assertThat(data.getTotalEmployees()).isEqualTo(4L);
        assertThat(data.getStatusDistribution()).hasSize(4);
        assertThat(response.getSummary()).contains("ACTIVE: 3 (75.0%)");
        assertThat(response.getSummary()).contains("ON_LEAVE: 1 (25.0%)");
    }

    @Test
    @DisplayName("generateReport discards placeholder responses from LLM and uses fallback")
    void generateReport_discardsPlaceholders() {
        WorkforceSummaryDto summaryDto = WorkforceSummaryDto.builder()
                .totalEmployees(5)
                .totalDepartments(1)
                .build();
        when(analyticsService.getWorkforceSummary()).thenReturn(summaryDto);

        org.springframework.ai.chat.model.ChatResponse mockAiResponse =
                new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage("[Insert employee count here] employees found."))));
        when(chatModel.call(any(Prompt.class))).thenReturn(mockAiResponse);

        ReportRequest request = ReportRequest.builder()
                .reportType(ReportType.WORKFORCE_OVERVIEW)
                .build();

        ReportResponse<?> response = workforceReportService.generateReport(request);

        // Placeholder must be discarded, falling back to deterministic text
        assertThat(response.getSummary()).doesNotContain("[Insert");
        assertThat(response.getSummary()).contains("5 staff members across 1 departments");
    }
}

