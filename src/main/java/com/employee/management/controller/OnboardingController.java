package com.employee.management.controller;

import com.employee.management.dto.ApiResponse;
import com.employee.management.dto.OnboardingChecklistRequest;
import com.employee.management.dto.OnboardingChecklistResponse;
import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.service.OnboardingService;
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
 * REST controller for AI Onboarding Assistant.
 * Exposes endpoints for generating tailored onboarding checklists and answering onboarding questions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/onboarding")
@Tag(name = "AI Onboarding Assistant", description = "Endpoints for generating role-specific onboarding checklists and answering policy-grounded onboarding questions")
public class OnboardingController {

    private final OnboardingService onboardingService;

    public OnboardingController(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @PostMapping("/checklist")
    @Operation(summary = "Generate a role- and department-specific onboarding checklist")
    public ResponseEntity<ApiResponse<OnboardingChecklistResponse>> generateChecklist(
            @Valid @RequestBody OnboardingChecklistRequest request) {
        log.info("Received request to generate onboarding checklist for employee='{}', role='{}'",
                request.getEmployeeName(), request.getJobRole());
        OnboardingChecklistResponse response = onboardingService.generateChecklist(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Onboarding checklist generated successfully"));
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask questions regarding company onboarding procedures with verified HR policy grounding")
    public ResponseEntity<ApiResponse<PolicyAnswerResponse>> askOnboardingQuestion(
            @Valid @RequestBody PolicyQuestionRequest request) {
        log.info("Received onboarding question: '{}'", request.getQuestion());
        PolicyAnswerResponse response = onboardingService.askOnboardingQuestion(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Onboarding question answered successfully"));
    }
}

