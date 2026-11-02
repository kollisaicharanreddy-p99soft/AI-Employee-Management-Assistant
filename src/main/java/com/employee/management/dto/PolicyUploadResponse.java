package com.employee.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyUploadResponse {

    private String documentId;
    private String fileName;
    private String fileType;
    private int chunkCount;
    private String status;
    private String message;
    private LocalDateTime uploadedAt;
}

