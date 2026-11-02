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
public class PolicyDocumentDto {

    private String documentId;
    private String fileName;
    private String fileType;
    private long fileSize;
    private int chunkCount;
    private LocalDateTime uploadedAt;
}

