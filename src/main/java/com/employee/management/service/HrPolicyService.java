package com.employee.management.service;

import com.employee.management.dto.PolicyAnswerResponse;
import com.employee.management.dto.PolicyDocumentDto;
import com.employee.management.dto.PolicyQuestionRequest;
import com.employee.management.dto.PolicySourceReference;
import com.employee.management.dto.PolicyUploadResponse;
import com.employee.management.exception.AiServiceException;
import com.employee.management.exception.DuplicateResourceException;
import com.employee.management.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for HR Policy Ingestion, Semantic Retrieval, and Grounded RAG Question Answering.
 *
 * <p>Separated cleanly from {@link AiChatService} to ensure data isolation.
 * Uses {@link VectorStore} (backed by {@link SimpleVectorStore}) for local similarity search.
 *
 * <p><strong>Persistence Notice:</strong>
 * In-memory storage is transient. Chunks are stored in memory and are lost on restart unless
 * saved to disk via {@code hr.policy.vector-store.file-path}.
 */
@Slf4j
@Service
public class HrPolicyService {

    public static final String FALLBACK_NO_INFO =
            "The available HR documents do not contain information to answer this question.";

    public static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final double SIMILARITY_THRESHOLD = 0.50;
    public static final int TOP_K_CHUNKS = 4;

    private static final String RAG_SYSTEM_PROMPT = """
            You are an authoritative HR Policy Assistant for the company.
            Your role is to answer user questions using EXCLUSIVELY the provided HR policy context excerpts.

            MANDATORY RULES:
            1. Rely ONLY on the clear facts directly mentioned in the Context below.
            2. Never invent policies, leave counts, limits, requirements, benefits, dates, or company procedures.
            3. Never assume or extrapolate rules not explicitly stated in the context.
            4. If the retrieved context does not contain sufficient details to answer the question, reply EXACTLY:
               "The available HR documents do not contain information to answer this question."
            5. Do NOT include placeholder text, fake citations, or example JSON in your answer.
            6. Keep your answer professional, clear, and direct.
            """;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final TokenTextSplitter tokenTextSplitter;

    @Value("${hr.policy.vector-store.file-path:}")
    private String vectorStoreFilePath;

    // Metadata registry for active policy documents
    private final Map<String, PolicyDocumentMetadata> documentRegistry = new ConcurrentHashMap<>();
    // Content hash registry to prevent duplicate ingestion: SHA256 -> documentId
    private final Map<String, String> contentHashRegistry = new ConcurrentHashMap<>();

    public HrPolicyService(VectorStore vectorStore, ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(RAG_SYSTEM_PROMPT)
                .build();
        this.tokenTextSplitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }

    /**
     * Ingests an HR policy file (.pdf or .txt), chunks it, generates embeddings,
     * and stores it in the vector store.
     */
    public PolicyUploadResponse uploadPolicy(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name must be specified");
        }

        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!extension.equals("pdf") && !extension.equals("txt")) {
            throw new IllegalArgumentException("Unsupported file type: ." + extension + ". Only PDF and TXT files are supported.");
        }

        byte[] contentBytes;
        try {
            contentBytes = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read file content", ex);
        }

        if (contentBytes.length == 0) {
            throw new IllegalArgumentException("Uploaded file contains zero bytes");
        }

        String contentHash = computeSha256(contentBytes);
        if (contentHashRegistry.containsKey(contentHash)) {
            String existingId = contentHashRegistry.get(contentHash);
            PolicyDocumentMetadata existing = documentRegistry.get(existingId);
            String existingName = existing != null ? existing.fileName : "existing document";
            throw new DuplicateResourceException("Policy document", "content hash", contentHash + " (matches '" + existingName + "')");
        }

        String documentId = UUID.randomUUID().toString();
        List<Document> rawDocuments;

        if (extension.equals("pdf")) {
            rawDocuments = parsePdf(contentBytes, originalFilename);
        } else {
            rawDocuments = parseTxt(contentBytes, originalFilename);
        }

        if (rawDocuments.isEmpty()) {
            throw new IllegalArgumentException("Document contains no extractable text");
        }

        // Split documents into chunks and enrich metadata
        List<Document> splitChunks = new ArrayList<>();
        List<String> chunkIds = new ArrayList<>();

        for (Document rawDoc : rawDocuments) {
            Integer pageNumber = null;
            Object pageObj = rawDoc.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
            if (pageObj instanceof Number number) {
                pageNumber = number.intValue();
            } else if (pageObj instanceof String str && !str.isBlank()) {
                try {
                    pageNumber = Integer.parseInt(str);
                } catch (NumberFormatException ignored) {
                }
            }

            List<Document> docChunks = tokenTextSplitter.split(rawDoc);
            for (Document chunk : docChunks) {
                String chunkId = UUID.randomUUID().toString();
                Document enriched = chunk.mutate()
                        .id(chunkId)
                        .metadata("documentId", documentId)
                        .metadata("fileName", originalFilename)
                        .metadata("fileType", extension)
                        .metadata("pageNumber", pageNumber != null ? pageNumber : 1)
                        .build();
                splitChunks.add(enriched);
                chunkIds.add(chunkId);
            }
        }

        if (splitChunks.isEmpty()) {
            throw new IllegalArgumentException("Document content was too short or empty to produce chunks");
        }

        // Store into vector store
        try {
            vectorStore.add(splitChunks);
            persistVectorStoreIfConfigured();
        } catch (Exception ex) {
            log.error("Failed to store chunks into vector store: {}", ex.getMessage(), ex);
            throw new AiServiceException("Failed to generate embeddings and store document: " + ex.getMessage(), ex);
        }

        // Save metadata in registry
        PolicyDocumentMetadata metadata = new PolicyDocumentMetadata(
                documentId,
                originalFilename,
                extension,
                file.getSize(),
                splitChunks.size(),
                chunkIds,
                contentHash,
                LocalDateTime.now()
        );
        documentRegistry.put(documentId, metadata);
        contentHashRegistry.put(contentHash, documentId);

        log.info("Successfully ingested document '{}' (ID: {}) with {} chunks",
                originalFilename, documentId, splitChunks.size());

        return PolicyUploadResponse.builder()
                .documentId(documentId)
                .fileName(originalFilename)
                .fileType(extension)
                .chunkCount(splitChunks.size())
                .status("SUCCESS")
                .message("Document successfully processed and indexed into vector store")
                .uploadedAt(metadata.uploadedAt)
                .build();
    }

    /**
     * Answers an HR policy question grounded strictly in retrieved context.
     */
    public PolicyAnswerResponse askQuestion(PolicyQuestionRequest request) {
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            throw new IllegalArgumentException("Question cannot be empty");
        }

        String question = request.getQuestion().trim();
        log.debug("Processing HR policy question: '{}'", question);

        // Check if there are any documents in the registry
        if (documentRegistry.isEmpty()) {
            return PolicyAnswerResponse.builder()
                    .question(question)
                    .answer(FALLBACK_NO_INFO)
                    .sources(Collections.emptyList())
                    .answeredAt(LocalDateTime.now())
                    .informationFound(false)
                    .build();
        }

        // Perform semantic retrieval
        List<Document> matchedDocuments;
        try {
            matchedDocuments = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(TOP_K_CHUNKS)
                            .similarityThreshold(SIMILARITY_THRESHOLD)
                            .build()
            );
        } catch (Exception ex) {
            log.error("Failed during vector similarity search: {}", ex.getMessage(), ex);
            throw new AiServiceException("Failed to search HR policy knowledge base: " + ex.getMessage(), ex);
        }

        if (matchedDocuments == null || matchedDocuments.isEmpty()) {
            log.info("No matching policy chunks found above similarity threshold for question: '{}'", question);
            return PolicyAnswerResponse.builder()
                    .question(question)
                    .answer(FALLBACK_NO_INFO)
                    .sources(Collections.emptyList())
                    .answeredAt(LocalDateTime.now())
                    .informationFound(false)
                    .build();
        }

        // Build sources
        List<PolicySourceReference> sources = new ArrayList<>();
        StringBuilder contextBuilder = new StringBuilder();

        for (int i = 0; i < matchedDocuments.size(); i++) {
            Document doc = matchedDocuments.get(i);
            String docName = Objects.toString(doc.getMetadata().get("fileName"), "Unknown Document");
            Integer page = null;
            Object pageObj = doc.getMetadata().get("pageNumber");
            if (pageObj instanceof Number num) {
                page = num.intValue();
            } else if (pageObj instanceof String s && !s.isBlank()) {
                try {
                    page = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                }
            }

            String content = doc.getText();
            String snippet = content != null && content.length() > 250 ? content.substring(0, 250) + "..." : content;

            sources.add(PolicySourceReference.builder()
                    .documentName(docName)
                    .pageNumber(page != null ? page : 1)
                    .snippet(snippet)
                    .score(doc.getScore())
                    .build());

            contextBuilder.append("--- Excerpt ").append(i + 1)
                    .append(" (Document: ").append(docName)
                    .append(", Page: ").append(page != null ? page : 1)
                    .append(") ---\n")
                    .append(content)
                    .append("\n\n");
        }

        String userPrompt = "Context excerpts from HR policy documents:\n"
                + contextBuilder
                + "User Question: " + question + "\n\n"
                + "Provide an accurate, grounded answer strictly based on the excerpts above. "
                + "If the excerpts do not contain the answer, reply with: "
                + "\"" + FALLBACK_NO_INFO + "\"";

        try {
            String answer = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                answer = FALLBACK_NO_INFO;
            }

            boolean infoFound = !answer.trim().equalsIgnoreCase(FALLBACK_NO_INFO);

            return PolicyAnswerResponse.builder()
                    .question(question)
                    .answer(answer.trim())
                    .sources(sources)
                    .answeredAt(LocalDateTime.now())
                    .informationFound(infoFound)
                    .build();

        } catch (Exception ex) {
            log.error("AI chat generation error during HR policy answering: {}", ex.getMessage(), ex);
            throw new AiServiceException("Failed to generate policy answer: " + ex.getMessage(), ex);
        }
    }

    /**
     * Lists all indexed policy documents and their metadata.
     */
    public List<PolicyDocumentDto> listPolicies() {
        return documentRegistry.values().stream()
                .map(meta -> PolicyDocumentDto.builder()
                        .documentId(meta.documentId)
                        .fileName(meta.fileName)
                        .fileType(meta.fileType)
                        .fileSize(meta.fileSize)
                        .chunkCount(meta.chunkCount)
                        .uploadedAt(meta.uploadedAt)
                        .build())
                .toList();
    }

    /**
     * Deletes a policy document, purging all vector entries and registry metadata.
     */
    public void deletePolicy(String documentId) {
        PolicyDocumentMetadata metadata = documentRegistry.get(documentId);
        if (metadata == null) {
            throw new ResourceNotFoundException("Policy document not found with ID: " + documentId);
        }

        try {
            if (metadata.chunkIds != null && !metadata.chunkIds.isEmpty()) {
                vectorStore.delete(metadata.chunkIds);
            }
            persistVectorStoreIfConfigured();
        } catch (Exception ex) {
            log.error("Failed to delete vector chunks for document {}: {}", documentId, ex.getMessage(), ex);
            throw new AiServiceException("Failed to delete document from vector store: " + ex.getMessage(), ex);
        }

        documentRegistry.remove(documentId);
        contentHashRegistry.remove(metadata.contentHash);
        log.info("Successfully deleted document '{}' (ID: {}) and its {} vector chunks",
                metadata.fileName, documentId, metadata.chunkCount);
    }

    private List<Document> parsePdf(byte[] bytes, String fileName) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        return reader.get();
    }

    private List<Document> parseTxt(byte[] bytes, String fileName) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return Collections.emptyList();
        }
        Document doc = new Document(text, Map.of("fileName", fileName, "pageNumber", 1));
        return List.of(doc);
    }

    private void persistVectorStoreIfConfigured() {
        if (vectorStoreFilePath != null && !vectorStoreFilePath.isBlank()
                && vectorStore instanceof SimpleVectorStore simpleStore) {
            try {
                simpleStore.save(new File(vectorStoreFilePath));
            } catch (Exception ex) {
                log.warn("Failed to persist SimpleVectorStore to disk at {}: {}",
                        vectorStoreFilePath, ex.getMessage());
            }
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1);
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private record PolicyDocumentMetadata(
            String documentId,
            String fileName,
            String fileType,
            long fileSize,
            int chunkCount,
            List<String> chunkIds,
            String contentHash,
            LocalDateTime uploadedAt
    ) {
    }
}
