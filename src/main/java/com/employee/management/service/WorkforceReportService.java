package com.employee.management.service;

import com.employee.management.dto.DepartmentDetailDto;
import com.employee.management.dto.DepartmentReportDataDto;
import com.employee.management.dto.ReportRequest;
import com.employee.management.dto.ReportResponse;
import com.employee.management.dto.ReportType;
import com.employee.management.dto.StatusDistributionDto;
import com.employee.management.dto.StatusReportDataDto;
import com.employee.management.dto.WorkforceSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that coordinates deterministic report generation and invokes Spring AI
 * solely to generate clear, professional summaries of the verified statistics.
 * If the model is unavailable or encounters an error, a deterministic fallback summary
 * is provided.
 */
@Slf4j
@Service
public class WorkforceReportService {

    private static final String REPORT_PROMPT_SYSTEM = """
            You are an HR analytics executive assistant. Your job is to write a concise,
            clear, and professional summary (2 to 4 sentences) of the provided verified HR report data.

            MANDATORY RULES:
            1. Use ONLY the exact numbers, percentages, and department names provided in the user data.
            2. Never invent, extrapolate, estimate, or assume any facts not explicitly present in the data.
            3. Do not include placeholders like [Insert count] or bracketed tokens.
            4. Do not include markdown tables, JSON snippets, or bulleted tool instructions.
            5. Provide clean, cohesive prose summarizing the key findings.
            """;

    private final AnalyticsService analyticsService;
    private final ChatClient chatClient;

    public WorkforceReportService(AnalyticsService analyticsService, ChatModel chatModel) {
        this.analyticsService = analyticsService;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(REPORT_PROMPT_SYSTEM)
                .build();
    }

    /**
     * Generates a workforce report based on the provided request.
     * All calculations are computed deterministically prior to invoking the LLM.
     *
     * @param request validated report request
     * @return structured report response with verified data and summary
     */
    public ReportResponse<?> generateReport(ReportRequest request) {
        if (request == null || request.getReportType() == null) {
            throw new IllegalArgumentException("Report type is required");
        }

        LocalDateTime generatedAt = LocalDateTime.now();
        ReportType reportType = request.getReportType();

        return switch (reportType) {
            case WORKFORCE_OVERVIEW -> buildWorkforceOverviewReport(generatedAt);
            case DEPARTMENT_REPORT -> buildDepartmentReport(request.getDepartmentName(), generatedAt);
            case EMPLOYEE_STATUS_REPORT -> buildEmployeeStatusReport(generatedAt);
        };
    }

    private ReportResponse<WorkforceSummaryDto> buildWorkforceOverviewReport(LocalDateTime generatedAt) {
        WorkforceSummaryDto data = analyticsService.getWorkforceSummary();

        String promptData = String.format(
                "Report: Workforce Overview. Total Employees: %d. Total Departments: %d. "
                        + "Status breakdown: %s. Department distribution: %s.",
                data.getTotalEmployees(),
                data.getTotalDepartments(),
                data.getCountByStatus() != null
                        ? data.getCountByStatus().stream()
                                .map(s -> s.getStatus() + ": " + s.getCount())
                                .collect(Collectors.joining(", "))
                        : "none",
                data.getCountByDepartment() != null
                        ? data.getCountByDepartment().stream()
                                .map(d -> d.getDepartmentName() + ": " + d.getCount())
                                .collect(Collectors.joining(", "))
                        : "none"
        );

        String fallbackSummary = buildWorkforceOverviewFallback(data);
        String summary = generateAiSummary(promptData, fallbackSummary);

        return ReportResponse.<WorkforceSummaryDto>builder()
                .reportType(ReportType.WORKFORCE_OVERVIEW)
                .generatedAt(generatedAt)
                .data(data)
                .summary(summary)
                .build();
    }

    private ReportResponse<DepartmentReportDataDto> buildDepartmentReport(String deptFilter, LocalDateTime generatedAt) {
        List<DepartmentDetailDto> departments = analyticsService.getDepartmentDetails(deptFilter);
        long totalEmployees = departments.stream().mapToLong(DepartmentDetailDto::getEmployeeCount).sum();

        DepartmentReportDataDto data = DepartmentReportDataDto.builder()
                .totalDepartments(departments.size())
                .totalEmployees(totalEmployees)
                .departments(departments)
                .build();

        String deptDetails = departments.isEmpty()
                ? "No departments found matching criteria."
                : departments.stream()
                        .map(d -> String.format("%s (ID: %d, Headcount: %d, Description: %s)",
                                d.getName(), d.getId(), d.getEmployeeCount(),
                                d.getDescription() != null ? d.getDescription() : "None"))
                        .collect(Collectors.joining("; "));

        String promptData = String.format(
                "Report: Department Report. Filter: %s. Total Departments: %d. Total Department Employees: %d. Departments: %s.",
                deptFilter != null ? deptFilter : "All",
                data.getTotalDepartments(),
                data.getTotalEmployees(),
                deptDetails
        );

        String fallbackSummary = buildDepartmentReportFallback(data, deptFilter);
        String summary = generateAiSummary(promptData, fallbackSummary);

        return ReportResponse.<DepartmentReportDataDto>builder()
                .reportType(ReportType.DEPARTMENT_REPORT)
                .generatedAt(generatedAt)
                .data(data)
                .summary(summary)
                .build();
    }

    private ReportResponse<StatusReportDataDto> buildEmployeeStatusReport(LocalDateTime generatedAt) {
        long totalEmployees = analyticsService.getTotalEmployeeCount();
        List<StatusDistributionDto> statusDistribution = analyticsService.getStatusDistribution();

        StatusReportDataDto data = StatusReportDataDto.builder()
                .totalEmployees(totalEmployees)
                .statusDistribution(statusDistribution)
                .build();

        String statusDetails = statusDistribution.stream()
                .map(s -> String.format("%s: %d (%.1f%%)", s.getStatus(), s.getCount(), s.getPercentage()))
                .collect(Collectors.joining(", "));

        String promptData = String.format(
                "Report: Employee Status Report. Total Employees: %d. Distribution: %s.",
                totalEmployees,
                statusDetails
        );

        String fallbackSummary = buildStatusReportFallback(data);
        String summary = generateAiSummary(promptData, fallbackSummary);

        return ReportResponse.<StatusReportDataDto>builder()
                .reportType(ReportType.EMPLOYEE_STATUS_REPORT)
                .generatedAt(generatedAt)
                .data(data)
                .summary(summary)
                .build();
    }

    private String generateAiSummary(String promptData, String fallbackSummary) {
        try {
            String response = chatClient.prompt()
                    .user("Summarize the following verified report data:\n" + promptData)
                    .call()
                    .content();

            if (response != null && !response.trim().isEmpty() && !response.contains("[Insert")) {
                return response.trim();
            }
            log.warn("Model returned blank or placeholder content. Using fallback summary.");
            return fallbackSummary;
        } catch (Exception ex) {
            log.warn("AI service call failed during report summarization ({}). Falling back to deterministic summary.",
                    ex.getMessage());
            return fallbackSummary;
        }
    }

    private String buildWorkforceOverviewFallback(WorkforceSummaryDto data) {
        if (data.getTotalEmployees() == 0) {
            return String.format("The organization currently has 0 employees across %d departments.",
                    data.getTotalDepartments());
        }

        String topDepts = data.getCountByDepartment() != null
                ? data.getCountByDepartment().stream()
                        .map(d -> d.getDepartmentName() + " (" + d.getCount() + ")")
                        .collect(Collectors.joining(", "))
                : "None";

        return String.format(
                "The organization currently employs %d staff members across %d departments. Department distribution: %s.",
                data.getTotalEmployees(),
                data.getTotalDepartments(),
                topDepts
        );
    }

    private String buildDepartmentReportFallback(DepartmentReportDataDto data, String filter) {
        if (data.getTotalDepartments() == 0) {
            return filter != null
                    ? String.format("No department records found matching '%s'.", filter)
                    : "No departments are currently registered in the organization.";
        }

        String breakdown = data.getDepartments().stream()
                .map(d -> String.format("%s: %d employees", d.getName(), d.getEmployeeCount()))
                .collect(Collectors.joining(", "));

        return String.format("The department report covers %d department(s) with a total of %d employees. Headcount breakdown: %s.",
                data.getTotalDepartments(), data.getTotalEmployees(), breakdown);
    }

    private String buildStatusReportFallback(StatusReportDataDto data) {
        if (data.getTotalEmployees() == 0) {
            return "The organization has 0 total employees. All employment status counts are currently at zero.";
        }

        String distributionText = data.getStatusDistribution().stream()
                .map(s -> String.format("%s: %d (%.1f%%)", s.getStatus(), s.getCount(), s.getPercentage()))
                .collect(Collectors.joining(", "));

        return String.format("Out of %d total employees, the status distribution is: %s.",
                data.getTotalEmployees(), distributionText);
    }
}

