package com.employee.management.controller;

import com.employee.management.dto.OnboardingChecklistRequest;
import com.employee.management.dto.OnboardingChecklistResponse;
import com.employee.management.dto.OnboardingStage;
import com.employee.management.dto.OnboardingTaskCategory;
import com.employee.management.dto.OnboardingTaskDto;
import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.service.OnboardingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OnboardingController.class)
@DisplayName("OnboardingController — web layer tests")
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OnboardingService onboardingService;

    @Test
    @DisplayName("POST /api/v1/ai/onboarding/checklist returns 200 with structured checklist")
    void generateChecklist_success() throws Exception {
        OnboardingChecklistRequest req = OnboardingChecklistRequest.builder()
                .employeeName("Alice Brown")
                .department("HR")
                .jobRole("HR Specialist")
                .joiningDate(LocalDate.of(2026, 11, 10))
                .experienceLevel("Mid-level")
                .build();

        OnboardingTaskDto task = OnboardingTaskDto.builder()
                .title("Submit Benefits Enrollment")
                .description("Fill out medical coverage forms.")
                .stage(OnboardingStage.DAY_ONE)
                .category(OnboardingTaskCategory.DOCUMENTATION)
                .policyGrounded(true)
                .policyReference("benefits_handbook.pdf")
                .build();

        OnboardingChecklistResponse res = OnboardingChecklistResponse.builder()
                .employeeName("Alice Brown")
                .department("HR")
                .jobRole("HR Specialist")
                .joiningDate(LocalDate.of(2026, 11, 10))
                .experienceLevel("Mid-level")
                .tasks(List.of(task))
                .totalTasks(1)
                .disclaimer(OnboardingService.ONBOARDING_DISCLAIMER)
                .generatedAt(LocalDateTime.now())
                .build();

        when(onboardingService.generateChecklist(any(OnboardingChecklistRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/ai/onboarding/checklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.employeeName").value("Alice Brown"))
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.tasks[0].title").value("Submit Benefits Enrollment"))
                .andExpect(jsonPath("$.data.tasks[0].policyGrounded").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/ai/onboarding/checklist returns 400 when required fields are blank")
    void generateChecklist_validationFailure() throws Exception {
        OnboardingChecklistRequest req = OnboardingChecklistRequest.builder()
                .employeeName("")
                .department("")
                .jobRole("")
                .build();

        mockMvc.perform(post("/api/v1/ai/onboarding/checklist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/ai/onboarding/ask returns 200 with grounded policy answer")
    void askOnboardingQuestion_success() throws Exception {
        PolicyQuestionRequest req = PolicyQuestionRequest.builder()
                .question("What are the mandatory onboarding documents?")
                .build();

        PolicyAnswerResponse res = PolicyAnswerResponse.builder()
                .question(req.getQuestion())
                .answer("Employees must provide government-issued ID and direct deposit details.")
                .informationFound(true)
                .answeredAt(LocalDateTime.now())
                .build();

        when(onboardingService.askOnboardingQuestion(any(PolicyQuestionRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/ai/onboarding/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.question").value("What are the mandatory onboarding documents?"))
                .andExpect(jsonPath("$.data.informationFound").value(true));
    }
}

