package com.employee.management.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportResponse<T> {

    private ReportType reportType;

    @Builder.Default
    private LocalDateTime generatedAt = LocalDateTime.now();

    /**
     * Authoritative, verified data calculated directly from the database.
     */
    private T data;

    /**
     * Readable human summary explaining the verified report data.
     */
    private String summary;
}

