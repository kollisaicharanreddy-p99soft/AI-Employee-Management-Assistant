package com.employee.management.service;

import com.employee.management.dto.OnboardingChecklistRequest;
import com.employee.management.dto.OnboardingChecklistResponse;
import com.employee.management.dto.OnboardingTaskCategory;
import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.model.Department;
import com.employee.management.model.Employee;
import com.employee.management.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OnboardingService Unit Tests")
class OnboardingServiceTest {

    @Mock
    private HrPolicyService hrPolicyService;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private ChatModel chatModel;

    private ObjectMapper objectMapper;
    private OnboardingService onboardingService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        onboardingService = new OnboardingService(
                hrPolicyService, vectorStore, employeeRepository, chatModel, objectMapper);
    }

    @Test
    @DisplayName("generateChecklist uses AI response when valid JSON is returned")
    void generateChecklist_withValidLlmOutput() {
        Document policyDoc = Document.builder()
                .text("All employees must submit identification documents.")
                .metadata(Map.of("fileName", "handbook.pdf"))
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(policyDoc));

        String mockLlmJson = """
                [
                  {
                    "title": "Complete Tax and Identification Forms",
                    "description": "Submit government ID and direct deposit.",
                    "stage": "PRE_BOARDING",
                    "category": "DOCUMENTATION",
                    "policyGrounded": true,
                    "policyReference": "handbook.pdf"
                  },
                  {
                    "title": "Setup Dev Laptop",
                    "description": "Configure IDE and Git keys.",
                    "stage": "DAY_ONE",
                    "category": "IT_SETUP",
                    "policyGrounded": false,
                    "policyReference": ""
                  }
                ]
                """;
        Generation gen = new Generation(new AssistantMessage(mockLlmJson));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(gen)));

        OnboardingChecklistRequest request = OnboardingChecklistRequest.builder()
                .employeeName("Jane Doe")
                .department("Engineering")
                .jobRole("Senior Backend Engineer")
                .joiningDate(LocalDate.of(2026, 12, 1))
                .experienceLevel("Senior")
                .build();

        OnboardingChecklistResponse response = onboardingService.generateChecklist(request);

        assertThat(response).isNotNull();
        assertThat(response.getEmployeeName()).isEqualTo("Jane Doe");
        assertThat(response.getDepartment()).isEqualTo("Engineering");
        assertThat(response.getJobRole()).isEqualTo("Senior Backend Engineer");
        assertThat(response.getTotalTasks()).isEqualTo(2);
        assertThat(response.getTasks().get(0).getTitle()).isEqualTo("Complete Tax and Identification Forms");
        assertThat(response.getTasks().get(0).isPolicyGrounded()).isTrue();
        assertThat(response.getTasks().get(0).getPolicyReference()).isEqualTo("handbook.pdf");
        assertThat(response.getTasks().get(1).isPolicyGrounded()).isFalse();
        assertThat(response.getDisclaimer()).isEqualTo(OnboardingService.ONBOARDING_DISCLAIMER);
    }

    @Test
    @DisplayName("generateChecklist falls back to deterministic checklist on LLM failure")
    void generateChecklist_llmFailure_usesFallback() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("Ollama connection error"));

        OnboardingChecklistRequest request = OnboardingChecklistRequest.builder()
                .employeeName("Alex Smith")
                .department("Sales")
                .jobRole("Account Executive")
                .joiningDate(LocalDate.now())
                .experienceLevel("Mid-level")
                .build();

        OnboardingChecklistResponse response = onboardingService.generateChecklist(request);

        assertThat(response).isNotNull();
        assertThat(response.getTasks()).isNotEmpty();
        assertThat(response.getTotalTasks()).isGreaterThanOrEqualTo(5);
        assertThat(response.getTasks().stream().anyMatch(t -> t.getCategory() == OnboardingTaskCategory.DOCUMENTATION)).isTrue();
        assertThat(response.getTasks().stream().anyMatch(t -> t.getCategory() == OnboardingTaskCategory.IT_SETUP)).isTrue();
        assertThat(response.getTasks().stream().anyMatch(t -> t.getCategory() == OnboardingTaskCategory.MANAGER_MEETING)).isTrue();
    }

    @Test
    @DisplayName("generateChecklist enriches details from EmployeeRepository if employeeId is provided")
    void generateChecklist_withEmployeeId_enrichesData() {
        Department dept = Department.builder().id(10L).name("Marketing").build();
        Employee emp = Employee.builder()
                .id(101L)
                .firstName("Robert")
                .lastName("Johnson")
                .department(dept)
                .jobTitle("Content Strategist")
                .build();
        when(employeeRepository.findById(101L)).thenReturn(Optional.of(emp));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(Collections.emptyList());
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM offline"));

        OnboardingChecklistRequest request = OnboardingChecklistRequest.builder()
                .employeeId(101L)
                .employeeName("")
                .department("")
                .jobRole("")
                .build();

        OnboardingChecklistResponse response = onboardingService.generateChecklist(request);

        assertThat(response.getEmployeeName()).isEqualTo("Robert Johnson");
        assertThat(response.getDepartment()).isEqualTo("Marketing");
        assertThat(response.getJobRole()).isEqualTo("Content Strategist");
    }

    @Test
    @DisplayName("askOnboardingQuestion delegates directly to HrPolicyService")
    void askOnboardingQuestion_delegatesToHrPolicyService() {
        PolicyQuestionRequest req = PolicyQuestionRequest.builder()
                .question("What is the onboarding procedure for new engineers?")
                .build();

        PolicyAnswerResponse expected = PolicyAnswerResponse.builder()
                .question(req.getQuestion())
                .answer("New engineers complete system onboarding on Day 1.")
                .informationFound(true)
                .build();

        when(hrPolicyService.askQuestion(req)).thenReturn(expected);

        PolicyAnswerResponse actual = onboardingService.askOnboardingQuestion(req);

        assertThat(actual).isSameAs(expected);
        verify(hrPolicyService).askQuestion(req);
    }
}

