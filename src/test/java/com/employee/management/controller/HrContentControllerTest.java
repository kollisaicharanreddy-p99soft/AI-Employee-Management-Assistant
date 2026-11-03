package com.employee.management.controller;

import com.employee.management.dto.EmailTone;
import com.employee.management.dto.HrEmailRequest;
import com.employee.management.dto.HrEmailResponse;
import com.employee.management.dto.HrEmailType;
import com.employee.management.dto.JobDescriptionRequest;
import com.employee.management.dto.JobDescriptionResponse;
import com.employee.management.service.HrContentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HrContentController.class)
@DisplayName("HrContentController — web layer tests")
class HrContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private HrContentService hrContentService;

    @Test
    @DisplayName("POST /api/v1/ai/hr-content/email returns 200 with generated email draft")
    void generateEmail_success() throws Exception {
        HrEmailRequest req = HrEmailRequest.builder()
                .emailType(HrEmailType.WELCOME)
                .recipientName("David")
                .tone(EmailTone.FRIENDLY)
                .context("Starting next Monday")
                .build();

        HrEmailResponse res = HrEmailResponse.builder()
                .emailType(HrEmailType.WELCOME)
                .subject("Welcome to our team, David!")
                .body("Hi David, welcome aboard!")
                .tone(EmailTone.FRIENDLY)
                .generatedAt(LocalDateTime.now())
                .build();

        when(hrContentService.generateEmail(any(HrEmailRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/ai/hr-content/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.emailType").value("WELCOME"))
                .andExpect(jsonPath("$.data.subject").value("Welcome to our team, David!"))
                .andExpect(jsonPath("$.data.tone").value("FRIENDLY"));
    }

    @Test
    @DisplayName("POST /api/v1/ai/hr-content/email returns 400 when emailType is missing")
    void generateEmail_missingType_badRequest() throws Exception {
        HrEmailRequest req = HrEmailRequest.builder().emailType(null).recipientName("David").build();

        mockMvc.perform(post("/api/v1/ai/hr-content/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /api/v1/ai/hr-content/job-description returns 200 with structured JD")
    void generateJobDescription_success() throws Exception {
        JobDescriptionRequest req = JobDescriptionRequest.builder()
                .jobTitle("DevOps Engineer")
                .department("Infrastructure")
                .experienceRange("3-5 years")
                .requiredSkills(List.of("Terraform", "Kubernetes", "AWS"))
                .build();

        JobDescriptionResponse res = JobDescriptionResponse.builder()
                .jobTitle("DevOps Engineer")
                .department("Infrastructure")
                .summary("Manage cloud infrastructure and CI/CD pipelines.")
                .responsibilities(List.of("Deploy cloud services", "Automate builds"))
                .requiredSkills(List.of("Terraform", "Kubernetes", "AWS"))
                .preferredQualifications(List.of("Go", "Python"))
                .experienceRequirements("3-5 years")
                .generatedAt(LocalDateTime.now())
                .build();

        when(hrContentService.generateJobDescription(any(JobDescriptionRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/ai/hr-content/job-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobTitle").value("DevOps Engineer"))
                .andExpect(jsonPath("$.data.department").value("Infrastructure"))
                .andExpect(jsonPath("$.data.responsibilities[0]").value("Deploy cloud services"))
                .andExpect(jsonPath("$.data.requiredSkills[0]").value("Terraform"));
    }

    @Test
    @DisplayName("POST /api/v1/ai/hr-content/job-description returns 400 on missing jobTitle or department")
    void generateJobDescription_missingFields_badRequest() throws Exception {
        JobDescriptionRequest req = JobDescriptionRequest.builder()
                .jobTitle("")
                .department("")
                .build();

        mockMvc.perform(post("/api/v1/ai/hr-content/job-description")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}

