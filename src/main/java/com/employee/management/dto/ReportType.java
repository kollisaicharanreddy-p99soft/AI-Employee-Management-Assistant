package com.employee.management.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportType {
    WORKFORCE_OVERVIEW,
    DEPARTMENT_REPORT,
    EMPLOYEE_STATUS_REPORT;

    @JsonCreator
    public static ReportType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Report type is required");
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (ReportType type : values()) {
            if (type.name().equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid report type: '" + value + "'. Supported types: WORKFORCE_OVERVIEW, DEPARTMENT_REPORT, EMPLOYEE_STATUS_REPORT");
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}

