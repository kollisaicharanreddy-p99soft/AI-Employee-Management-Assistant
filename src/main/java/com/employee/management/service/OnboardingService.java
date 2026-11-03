package com.employee.management.service;

import com.employee.management.dto.OnboardingChecklistRequest;
import com.employee.management.dto.OnboardingChecklistResponse;
import com.employee.management.dto.OnboardingStage;
import com.employee.management.dto.OnboardingTaskCategory;
import com.employee.management.dto.OnboardingTaskDto;
import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.model.Employee;
import com.employee.management.repository.EmployeeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for generating role/department specific Onboarding Checklists
 * and answering onboarding procedural questions grounded in HR policy documents.
 */
@Slf4j
@Service
public class OnboardingService {

    public static final String ONBOARDING_DISCLAIMER =
            "Suggested tasks are generated based on role and department requirements. Tasks marked as policy-grounded correspond directly to verified company HR policy documentation.";

    private final HrPolicyService hrPolicyService;
    private final VectorStore vectorStore;
    private final EmployeeRepository employeeRepository;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public OnboardingService(HrPolicyService hrPolicyService,
                             VectorStore vectorStore,
                             EmployeeRepository employeeRepository,
                             ChatModel chatModel,
                             ObjectMapper objectMapper) {
        this.hrPolicyService = hrPolicyService;
        this.vectorStore = vectorStore;
        this.employeeRepository = employeeRepository;
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a tailored onboarding checklist based on employee role, department,
     * experience level, and existing HR policy documentation.
     */
    public OnboardingChecklistResponse generateChecklist(OnboardingChecklistRequest request) {
        log.info("Generating onboarding checklist for employee='{}', department='{}', role='{}'",
                request.getEmployeeName(), request.getDepartment(), request.getJobRole());

        // Read-only employee enrichment if ID is provided
        String candidateName = request.getEmployeeName();
        String candidateDept = request.getDepartment();
        String candidateRole = request.getJobRole();

        if (request.getEmployeeId() != null) {
            Optional<Employee> empOpt = employeeRepository.findById(request.getEmployeeId());
            if (empOpt.isPresent()) {
                Employee emp = empOpt.get();
                if (candidateName == null || candidateName.isBlank()) {
                    candidateName = emp.getFirstName() + " " + emp.getLastName();
                }
                if ((candidateDept == null || candidateDept.isBlank()) && emp.getDepartment() != null) {
                    candidateDept = emp.getDepartment().getName();
                }
                if (candidateRole == null || candidateRole.isBlank()) {
                    candidateRole = emp.getJobTitle();
                }
            }
        }

        // Retrieve relevant HR policy documentation from VectorStore
        List<Document> policyDocs = List.of();
        try {
            policyDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("onboarding documentation orientation IT setup policy " + candidateDept)
                            .topK(3)
                            .similarityThreshold(0.50)
                            .build()
            );
        } catch (Exception ex) {
            log.warn("Vector store lookup failed during checklist generation: {}. Proceeding without policy excerpts.", ex.getMessage());
        }

        StringBuilder policyContext = new StringBuilder();
        if (policyDocs != null && !policyDocs.isEmpty()) {
            for (Document doc : policyDocs) {
                String docName = String.valueOf(doc.getMetadata().getOrDefault("fileName", "HR Policy"));
                policyContext.append("- [Source: ").append(docName).append("]: ").append(doc.getText()).append("\n");
            }
        }

        // Build prompt for LLM
        String systemPrompt = """
                You are an expert HR Onboarding Specialist.
                Generate a structured JSON array of onboarding tasks for a new hire.
                Each task must have:
                - "title": concise task title
                - "description": clear actionable description
                - "stage": one of PRE_BOARDING, DAY_ONE, FIRST_WEEK, FIRST_MONTH
                - "category": one of DOCUMENTATION, HR_ORIENTATION, POLICY_REVIEW, DEPARTMENT_INTRODUCTION, ROLE_SPECIFIC_TRAINING, IT_SETUP, MANAGER_MEETING, FIRST_WEEK_ACTIVITY
                - "policyGrounded": boolean true IF the task directly relates to the provided HR Policy Context, false otherwise
                - "policyReference": document name if policyGrounded is true, otherwise empty string ""

                RULES:
                1. Only output a valid JSON array of objects. Do not include markdown code blocks (like ```json), commentary, or extra text.
                2. Tasks must cover documentation, IT setup, HR orientation, manager meeting, and department/role training.
                3. Do not invent specific unauthorized company benefits or rules.
                """;

        String userPrompt = String.format("""
                Employee Name: %s
                Department: %s
                Job Role: %s
                Experience Level: %s
                Joining Date: %s

                Verified HR Policy Context:
                %s
                """,
                candidateName,
                candidateDept,
                candidateRole,
                request.getExperienceLevel() != null ? request.getExperienceLevel() : "Standard",
                request.getJoiningDate() != null ? request.getJoiningDate().toString() : "Not specified",
                policyContext.length() > 0 ? policyContext.toString() : "None available."
        );

        List<OnboardingTaskDto> tasks = new ArrayList<>();
        try {
            String rawJson = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (rawJson != null && !rawJson.isBlank()) {
                tasks = parseTasks(rawJson);
            }
        } catch (Exception ex) {
            log.warn("Failed to generate AI onboarding tasks from LLM: {}. Using deterministic default checklist.", ex.getMessage());
        }

        if (tasks.isEmpty()) {
            tasks = generateDefaultChecklist(candidateDept, candidateRole, policyDocs);
        }

        return OnboardingChecklistResponse.builder()
                .employeeId(request.getEmployeeId())
                .employeeName(candidateName)
                .department(candidateDept)
                .jobRole(candidateRole)
                .joiningDate(request.getJoiningDate())
                .experienceLevel(request.getExperienceLevel())
                .tasks(tasks)
                .totalTasks(tasks.size())
                .disclaimer(ONBOARDING_DISCLAIMER)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Answers onboarding procedural questions grounded in HR policy documents.
     * Reuses Day 2 RAG retrieval service directly.
     */
    public PolicyAnswerResponse askOnboardingQuestion(PolicyQuestionRequest request) {
        log.info("Processing onboarding procedural question: '{}'", request.getQuestion());
        return hrPolicyService.askQuestion(request);
    }

    private List<OnboardingTaskDto> parseTasks(String rawJson) {
        try {
            String cleanJson = rawJson.trim();
            if (cleanJson.startsWith("```json")) {
                cleanJson = cleanJson.substring(7);
            } else if (cleanJson.startsWith("```")) {
                cleanJson = cleanJson.substring(3);
            }
            if (cleanJson.endsWith("```")) {
                cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
            }
            cleanJson = cleanJson.trim();

            return objectMapper.readValue(cleanJson, new TypeReference<List<OnboardingTaskDto>>() {});
        } catch (Exception e) {
            log.warn("Could not deserialize AI tasks JSON: {}. Falling back to default checklist.", e.getMessage());
            return List.of();
        }
    }

    private List<OnboardingTaskDto> generateDefaultChecklist(String department, String role, List<Document> policyDocs) {
        boolean hasPolicy = policyDocs != null && !policyDocs.isEmpty();
        String policyRef = hasPolicy ? String.valueOf(policyDocs.get(0).getMetadata().getOrDefault("fileName", "HR Policy")) : "";

        return List.of(
                OnboardingTaskDto.builder()
                        .title("Submit Identification and Tax Documents")
                        .description("Provide signed tax forms, government ID, and direct deposit authorization.")
                        .stage(OnboardingStage.PRE_BOARDING)
                        .category(OnboardingTaskCategory.DOCUMENTATION)
                        .policyGrounded(hasPolicy)
                        .policyReference(policyRef)
                        .build(),
                OnboardingTaskDto.builder()
                        .title("IT Account Setup & Equipment Provisioning")
                        .description("Receive workstation credentials, email setup, and security access cards.")
                        .stage(OnboardingStage.DAY_ONE)
                        .category(OnboardingTaskCategory.IT_SETUP)
                        .policyGrounded(false)
                        .policyReference("")
                        .build(),
                OnboardingTaskDto.builder()
                        .title("Attend HR Orientation")
                        .description("Review company handbook, benefits summary, and standard workplace policies.")
                        .stage(OnboardingStage.DAY_ONE)
                        .category(OnboardingTaskCategory.HR_ORIENTATION)
                        .policyGrounded(hasPolicy)
                        .policyReference(policyRef)
                        .build(),
                OnboardingTaskDto.builder()
                        .title("1-on-1 Meeting with Manager")
                        .description("Discuss role objectives, 30-day goals, and immediate team priorities.")
                        .stage(OnboardingStage.DAY_ONE)
                        .category(OnboardingTaskCategory.MANAGER_MEETING)
                        .policyGrounded(false)
                        .policyReference("")
                        .build(),
                OnboardingTaskDto.builder()
                        .title("Team Introduction in " + department)
                        .description("Meet team members and key stakeholders across the " + department + " department.")
                        .stage(OnboardingStage.FIRST_WEEK)
                        .category(OnboardingTaskCategory.DEPARTMENT_INTRODUCTION)
                        .policyGrounded(false)
                        .policyReference("")
                        .build(),
                OnboardingTaskDto.builder()
                        .title("Role-Specific Training for " + role)
                        .description("Complete introductory workflows, development setup, and tools training.")
                        .stage(OnboardingStage.FIRST_WEEK)
                        .category(OnboardingTaskCategory.ROLE_SPECIFIC_TRAINING)
                        .policyGrounded(false)
                        .policyReference("")
                        .build(),
                OnboardingTaskDto.builder()
                        .title("Review Code of Conduct & Compliance")
                        .description("Complete mandatory compliance modules and acknowledge company policies.")
                        .stage(OnboardingStage.FIRST_WEEK)
                        .category(OnboardingTaskCategory.POLICY_REVIEW)
                        .policyGrounded(hasPolicy)
                        .policyReference(policyRef)
                        .build(),
                OnboardingTaskDto.builder()
                        .title("First 30-Day Check-in")
                        .description("Review initial accomplishments and feedback with reporting manager.")
                        .stage(OnboardingStage.FIRST_MONTH)
                        .category(OnboardingTaskCategory.FIRST_WEEK_ACTIVITY)
                        .policyGrounded(false)
                        .policyReference("")
                        .build()
        );
    }
}

