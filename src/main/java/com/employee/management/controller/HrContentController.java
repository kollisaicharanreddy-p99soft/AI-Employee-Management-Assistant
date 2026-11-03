package com.employee.management.controller;

import com.employee.management.dto.ApiResponse;
import com.employee.management.dto.HrEmailRequest;
import com.employee.management.dto.HrEmailResponse;
import com.employee.management.dto.JobDescriptionRequest;
import com.employee.management.dto.JobDescriptionResponse;
import com.employee.management.service.HrContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for AI HR Content Assistant.
 * Exposes endpoints for drafting HR emails and generating structured job descriptions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/hr-content")
@Tag(name = "AI HR Content Assistant", description = "Endpoints for drafting HR emails and generating job descriptions")
public class HrContentController {

    private final HrContentService hrContentService;

    public HrContentController(HrContentService hrContentService) {
        this.hrContentService = hrContentService;
    }

    @PostMapping("/email")
    @Operation(summary = "Draft a professional HR email with custom tone and context")
    public ResponseEntity<ApiResponse<HrEmailResponse>> generateEmail(
            @Valid @RequestBody HrEmailRequest request) {
        log.info("Received request to draft HR email of type='{}'", request.getEmailType());
        HrEmailResponse response = hrContentService.generateEmail(request);
        return ResponseEntity.ok(ApiResponse.success(response, "HR email draft generated successfully"));
    }

    @PostMapping("/job-description")
    @Operation(summary = "Generate a structured job description with responsibilities and skills")
    public ResponseEntity<ApiResponse<JobDescriptionResponse>> generateJobDescription(
            @Valid @RequestBody JobDescriptionRequest request) {
        log.info("Received request to generate job description for title='{}'", request.getJobTitle());
        JobDescriptionResponse response = hrContentService.generateJobDescription(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Job description generated successfully"));
    }
}

