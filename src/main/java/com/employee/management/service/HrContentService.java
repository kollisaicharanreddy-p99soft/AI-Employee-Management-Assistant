package com.employee.management.service;

import com.employee.management.dto.EmailTone;
import com.employee.management.dto.HrEmailRequest;
import com.employee.management.dto.HrEmailResponse;
import com.employee.management.dto.HrEmailType;
import com.employee.management.dto.JobDescriptionRequest;
import com.employee.management.dto.JobDescriptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for generating HR content: professional email drafts and structured job descriptions.
 * Purely draft generation — no external emails are dispatched.
 */
@Slf4j
@Service
public class HrContentService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public HrContentService(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Generates a professional HR email draft based on email type, recipient, context, and tone.
     * Uses placeholders like [Date], [Company Name] if specific details are not provided.
     */
    public HrEmailResponse generateEmail(HrEmailRequest request) {
        if (request == null || request.getEmailType() == null) {
            throw new IllegalArgumentException("Email type is required");
        }

        EmailTone tone = request.getTone() != null ? request.getTone() : EmailTone.FORMAL;
        String recipient = request.getRecipientName() != null && !request.getRecipientName().isBlank()
                ? request.getRecipientName().trim() : "[Recipient Name]";

        log.info("Generating HR email draft of type='{}' for recipient='{}' with tone='{}'",
                request.getEmailType(), recipient, tone);

        String systemPrompt = """
                You are an expert HR Communications Specialist.
                Your task is to draft a professional, grammatically correct HR email.

                STRICT RULES:
                1. Match the specified tone:
                   - FORMAL: Polished, professional, respectful, standard corporate tone.
                   - FRIENDLY: Warm, welcoming, encouraging, approachable yet professional.
                   - CONCISE: Direct, brief, bulleted where appropriate, minimal pleasantries.
                2. Do NOT invent dates, approval decisions, employee records, or unauthorized company policies.
                3. If specific details (e.g., date, time, manager name, job title) are missing from the context, use clear bracketed placeholders such as [Date], [Time], [Job Title], [Manager Name].
                4. Output your response as a valid JSON object with exactly two keys:
                   - "subject": concise and descriptive email subject line
                   - "body": the complete formatted email body (including salutation, body paragraphs, and sign-off)
                5. Do NOT include markdown code fences (like ```json), commentary, or extra text. Output raw JSON only.
                """;

        String userPrompt = String.format("""
                Email Type: %s
                Recipient Name: %s
                Tone: %s
                Context: %s
                Additional Instructions: %s
                """,
                request.getEmailType().name(),
                recipient,
                tone.name(),
                request.getContext() != null ? request.getContext() : "Standard HR communication",
                request.getAdditionalInstructions() != null ? request.getAdditionalInstructions() : "None"
        );

        String subject;
        String body;

        try {
            String rawJson = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            EmailDraftJson parsed = parseEmailJson(rawJson);
            if (parsed != null && parsed.subject() != null && parsed.body() != null) {
                subject = parsed.subject().trim();
                body = parsed.body().trim();
            } else {
                throw new IllegalStateException("LLM response did not contain valid subject and body");
            }
        } catch (Exception ex) {
            log.warn("Failed to generate email via LLM: {}. Falling back to deterministic template.", ex.getMessage());
            EmailDraftJson fallback = generateFallbackEmail(request.getEmailType(), recipient, tone, request.getContext());
            subject = fallback.subject();
            body = fallback.body();
        }

        return HrEmailResponse.builder()
                .emailType(request.getEmailType())
                .subject(subject)
                .body(body)
                .tone(tone)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Generates a structured Job Description based on job title, department, skills, and experience.
     */
    public JobDescriptionResponse generateJobDescription(JobDescriptionRequest request) {
        if (request == null || request.getJobTitle() == null || request.getJobTitle().isBlank()) {
            throw new IllegalArgumentException("Job title is required");
        }
        if (request.getDepartment() == null || request.getDepartment().isBlank()) {
            throw new IllegalArgumentException("Department is required");
        }

        log.info("Generating job description for title='{}', department='{}'",
                request.getJobTitle(), request.getDepartment());

        String systemPrompt = """
                You are an expert HR Talent Acquisition Specialist.
                Generate a comprehensive, structured job description.

                STRICT RULES:
                1. Avoid discriminatory or irrelevant requirements.
                2. Do NOT invent company-specific salary numbers or proprietary internal benefits.
                3. Output your response as a valid JSON object with the following keys:
                   - "summary": compelling 2-3 sentence overview of the role
                   - "responsibilities": JSON array of strings, listing 4-6 primary job duties
                   - "requiredSkills": JSON array of strings, listing required skills and competencies
                   - "preferredQualifications": JSON array of strings, listing 2-4 nice-to-have qualifications
                   - "experienceRequirements": string describing the required experience level
                4. Do NOT include markdown code fences (like ```json), commentary, or extra text. Output raw JSON only.
                """;

        String userPrompt = String.format("""
                Job Title: %s
                Department: %s
                Experience Range: %s
                Required Skills: %s
                Specified Responsibilities: %s
                Additional Instructions: %s
                """,
                request.getJobTitle(),
                request.getDepartment(),
                request.getExperienceRange() != null ? request.getExperienceRange() : "Relevant industry experience",
                request.getRequiredSkills() != null ? String.join(", ", request.getRequiredSkills()) : "Standard domain competencies",
                request.getResponsibilities() != null ? String.join("; ", request.getResponsibilities()) : "Standard role duties",
                request.getAdditionalInstructions() != null ? request.getAdditionalInstructions() : "None"
        );

        JobDescriptionJson parsed = null;
        try {
            String rawJson = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            parsed = parseJobDescriptionJson(rawJson);
        } catch (Exception ex) {
            log.warn("Failed to generate job description via LLM: {}. Falling back to structured default.", ex.getMessage());
        }

        if (parsed == null) {
            parsed = generateFallbackJobDescription(request);
        }

        return JobDescriptionResponse.builder()
                .jobTitle(request.getJobTitle())
                .department(request.getDepartment())
                .summary(parsed.summary())
                .responsibilities(parsed.responsibilities())
                .requiredSkills(parsed.requiredSkills())
                .preferredQualifications(parsed.preferredQualifications())
                .experienceRequirements(parsed.experienceRequirements())
                .generatedAt(LocalDateTime.now())
                .build();
    }

    private EmailDraftJson parseEmailJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            String clean = cleanJson(rawJson);
            return objectMapper.readValue(clean, EmailDraftJson.class);
        } catch (Exception e) {
            log.debug("Could not parse email JSON: {}", e.getMessage());
            return null;
        }
    }

    private JobDescriptionJson parseJobDescriptionJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            String clean = cleanJson(rawJson);
            return objectMapper.readValue(clean, JobDescriptionJson.class);
        } catch (Exception e) {
            log.debug("Could not parse job description JSON: {}", e.getMessage());
            return null;
        }
    }

    private String cleanJson(String rawJson) {
        String clean = rawJson.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }

    private EmailDraftJson generateFallbackEmail(HrEmailType type, String recipient, EmailTone tone, String context) {
        String salutation = tone == EmailTone.FRIENDLY ? "Hi " + recipient + "," : "Dear " + recipient + ",";
        String signoff = tone == EmailTone.FRIENDLY ? "Best regards,\nHR Team" : "Sincerely,\nHuman Resources Department";

        return switch (type) {
            case WELCOME -> new EmailDraftJson(
                    "Welcome to the Team, " + recipient + "!",
                    salutation + "\n\nWelcome to our team! We are thrilled to have you join us starting on [Start Date]. "
                            + "Please find your onboarding details attached. Let us know if you have any questions before your first day.\n\n"
                            + signoff
            );
            case INTERVIEW_INVITATION -> new EmailDraftJson(
                    "Interview Invitation: [Job Title] Role",
                    salutation + "\n\nThank you for applying. We were impressed with your background and would like to invite you for an interview on [Date] at [Time]. "
                            + "Please confirm your availability.\n\n"
                            + signoff
            );
            case INTERVIEW_CONFIRMATION -> new EmailDraftJson(
                    "Confirmation: Interview for [Job Title] Role",
                    salutation + "\n\nThis email confirms your upcoming interview scheduled for [Date] at [Time]. "
                            + "We look forward to speaking with you.\n\n"
                            + signoff
            );
            case LEAVE_APPROVAL -> new EmailDraftJson(
                    "Leave Request Approved",
                    salutation + "\n\nYour leave request for [Start Date] through [End Date] has been approved. "
                            + "Enjoy your time off.\n\n"
                            + signoff
            );
            case LEAVE_REJECTION -> new EmailDraftJson(
                    "Update on Leave Request",
                    salutation + "\n\nThank you for your leave application. Unfortunately, due to operational requirements during [Dates], your request could not be approved at this time. "
                            + "Please reach out to your manager to discuss alternative dates.\n\n"
                            + signoff
            );
            case EMPLOYEE_ANNOUNCEMENT -> new EmailDraftJson(
                    "Company Announcement: [Topic]",
                    salutation + "\n\nPlease be advised of the following company announcement:\n\n"
                            + (context != null ? context : "[Announcement Details]") + "\n\n"
                            + signoff
            );
            case GENERAL_COMMUNICATION -> new EmailDraftJson(
                    "HR Communication: [Subject]",
                    salutation + "\n\n" + (context != null ? context : "This is an official communication from Human Resources.") + "\n\n"
                            + signoff
            );
        };
    }

    private JobDescriptionJson generateFallbackJobDescription(JobDescriptionRequest request) {
        List<String> skills = (request.getRequiredSkills() != null && !request.getRequiredSkills().isEmpty())
                ? request.getRequiredSkills()
                : List.of("Strong analytical and problem-solving skills", "Effective communication and teamwork", "Proficiency in role-relevant tools");

        List<String> responsibilities = (request.getResponsibilities() != null && !request.getResponsibilities().isEmpty())
                ? request.getResponsibilities()
                : List.of("Execute daily deliverables and strategic initiatives in the " + request.getDepartment() + " department",
                "Collaborate with cross-functional teams to achieve organizational goals",
                "Maintain high standards of quality, security, and process compliance");

        List<String> preferred = List.of(
                "Prior experience in a fast-paced environment",
                "Demonstrated track record of successful project execution"
        );

        String experience = (request.getExperienceRange() != null && !request.getExperienceRange().isBlank())
                ? request.getExperienceRange()
                : "2-5 years of relevant industry experience";

        return new JobDescriptionJson(
                "We are seeking a talented " + request.getJobTitle() + " to join our " + request.getDepartment() + " team and drive excellence in organizational initiatives.",
                responsibilities,
                skills,
                preferred,
                experience
        );
    }

    private record EmailDraftJson(String subject, String body) {}

    private record JobDescriptionJson(
            String summary,
            List<String> responsibilities,
            List<String> requiredSkills,
            List<String> preferredQualifications,
            String experienceRequirements
    ) {}
}

