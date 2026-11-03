package com.employee.management.service;

import com.employee.management.dto.EmailTone;
import com.employee.management.dto.HrEmailRequest;
import com.employee.management.dto.HrEmailResponse;
import com.employee.management.dto.HrEmailType;
import com.employee.management.dto.JobDescriptionRequest;
import com.employee.management.dto.JobDescriptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HrContentService Unit Tests")
class HrContentServiceTest {

    @Mock
    private ChatModel chatModel;

    private ObjectMapper objectMapper;
    private HrContentService hrContentService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        hrContentService = new HrContentService(chatModel, objectMapper);
    }

    @Test
    @DisplayName("generateEmail parses valid LLM JSON output")
    void generateEmail_validLlmOutput() {
        String mockJson = """
                {
                  "subject": "Welcome to Acme Corp, John!",
                  "body": "Dear John,\\n\\nWe are thrilled to welcome you to the team.\\n\\nBest,\\nHR"
                }
                """;
        Generation gen = new Generation(new AssistantMessage(mockJson));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(gen)));

        HrEmailRequest request = HrEmailRequest.builder()
                .emailType(HrEmailType.WELCOME)
                .recipientName("John")
                .tone(EmailTone.FRIENDLY)
                .context("Starting Monday")
                .build();

        HrEmailResponse response = hrContentService.generateEmail(request);

        assertThat(response).isNotNull();
        assertThat(response.getSubject()).isEqualTo("Welcome to Acme Corp, John!");
        assertThat(response.getBody()).contains("Dear John");
        assertThat(response.getTone()).isEqualTo(EmailTone.FRIENDLY);
        assertThat(response.getEmailType()).isEqualTo(HrEmailType.WELCOME);
    }

    @ParameterizedTest
    @EnumSource(HrEmailType.class)
    @DisplayName("generateEmail falls back gracefully for all email types when LLM fails")
    void generateEmail_allTypes_fallback(HrEmailType type) {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM offline"));

        HrEmailRequest request = HrEmailRequest.builder()
                .emailType(type)
                .recipientName("Alice")
                .tone(EmailTone.FORMAL)
                .context("Annual performance discussion")
                .build();

        HrEmailResponse response = hrContentService.generateEmail(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmailType()).isEqualTo(type);
        assertThat(response.getSubject()).isNotBlank();
        assertThat(response.getBody()).isNotBlank();
        assertThat(response.getBody()).contains("Alice");
    }

    @Test
    @DisplayName("generateEmail uses placeholder when recipientName is missing")
    void generateEmail_missingRecipient_usesPlaceholder() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM offline"));

        HrEmailRequest request = HrEmailRequest.builder()
                .emailType(HrEmailType.INTERVIEW_INVITATION)
                .recipientName(null)
                .tone(EmailTone.FORMAL)
                .build();

        HrEmailResponse response = hrContentService.generateEmail(request);

        assertThat(response.getBody()).contains("[Recipient Name]");
    }

    @Test
    @DisplayName("generateEmail throws IllegalArgumentException on null type")
    void generateEmail_nullType_throwsException() {
        HrEmailRequest request = HrEmailRequest.builder().emailType(null).build();
        assertThatThrownBy(() -> hrContentService.generateEmail(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("generateJobDescription parses valid LLM JSON output")
    void generateJobDescription_validLlmOutput() {
        String mockJson = """
                {
                  "summary": "Join our Engineering team as a Senior Java Engineer building cloud services.",
                  "responsibilities": [
                    "Design and implement scalable microservices",
                    "Lead architectural design reviews",
                    "Mentor junior developers"
                  ],
                  "requiredSkills": ["Java 21", "Spring Boot", "Docker", "PostgreSQL"],
                  "preferredQualifications": ["Kubernetes", "AWS certification"],
                  "experienceRequirements": "5+ years of software development experience"
                }
                """;
        Generation gen = new Generation(new AssistantMessage(mockJson));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(gen)));

        JobDescriptionRequest request = JobDescriptionRequest.builder()
                .jobTitle("Senior Java Engineer")
                .department("Engineering")
                .experienceRange("5+ years")
                .requiredSkills(List.of("Java", "Spring Boot"))
                .build();

        JobDescriptionResponse response = hrContentService.generateJobDescription(request);

        assertThat(response).isNotNull();
        assertThat(response.getJobTitle()).isEqualTo("Senior Java Engineer");
        assertThat(response.getDepartment()).isEqualTo("Engineering");
        assertThat(response.getSummary()).contains("cloud services").doesNotContain("discriminatory");
        assertThat(response.getResponsibilities()).hasSize(3);
        assertThat(response.getRequiredSkills()).contains("Java 21");
        assertThat(response.getPreferredQualifications()).contains("Kubernetes");
    }

    @Test
    @DisplayName("generateJobDescription uses structured fallback when LLM fails")
    void generateJobDescription_llmFailure_usesFallback() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Ollama unreachable"));

        JobDescriptionRequest request = JobDescriptionRequest.builder()
                .jobTitle("Product Manager")
                .department("Product")
                .experienceRange("3-5 years")
                .requiredSkills(List.of("Roadmapping", "Agile"))
                .responsibilities(List.of("Define product vision"))
                .build();

        JobDescriptionResponse response = hrContentService.generateJobDescription(request);

        assertThat(response).isNotNull();
        assertThat(response.getJobTitle()).isEqualTo("Product Manager");
        assertThat(response.getDepartment()).isEqualTo("Product");
        assertThat(response.getSummary()).contains("Product Manager");
        assertThat(response.getRequiredSkills()).contains("Roadmapping");
        assertThat(response.getResponsibilities()).contains("Define product vision");
        assertThat(response.getExperienceRequirements()).isEqualTo("3-5 years");
    }

    @Test
    @DisplayName("generateJobDescription throws IllegalArgumentException on missing title or department")
    void generateJobDescription_missingRequiredFields_throwsException() {
        JobDescriptionRequest req1 = JobDescriptionRequest.builder().jobTitle("").department("Eng").build();
        assertThatThrownBy(() -> hrContentService.generateJobDescription(req1))
                .isInstanceOf(IllegalArgumentException.class);

        JobDescriptionRequest req2 = JobDescriptionRequest.builder().jobTitle("Engineer").department("").build();
        assertThatThrownBy(() -> hrContentService.generateJobDescription(req2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
