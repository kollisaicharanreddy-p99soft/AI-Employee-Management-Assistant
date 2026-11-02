package com.employee.management.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Configuration for Vector Store in HR Policy Assistant.
 *
 * <p>Uses {@link SimpleVectorStore} for local development, backed by the application's
 * configured {@link EmbeddingModel} (e.g. Ollama).
 *
 * <p><strong>Note on Persistence:</strong>
 * In-memory vector storage in {@link SimpleVectorStore} is transient and will be lost
 * across application restarts unless a persistence file path is configured via
 * {@code hr.policy.vector-store.file-path} and saved using {@link SimpleVectorStore#save(File)}.
 */
@Configuration
public class VectorStoreConfig {

    @Value("${hr.policy.vector-store.file-path:}")
    private String vectorStoreFilePath;

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        if (vectorStoreFilePath != null && !vectorStoreFilePath.isBlank()) {
            File file = new File(vectorStoreFilePath);
            if (file.exists() && file.isFile()) {
                simpleVectorStore.load(file);
            }
        }
        return simpleVectorStore;
    }
}

