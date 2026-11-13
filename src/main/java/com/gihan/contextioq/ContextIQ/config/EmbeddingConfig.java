package com.gihan.contextioq.ContextIQ.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class EmbeddingConfig {
    @Bean
    public EmbeddingModel embeddingModel(@Value("${contextiq.ai.base-url}") String url,
                                         @Value("${contextiq.rag.embedding-model}") String model) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(120));
        var api = OllamaApi.builder().baseUrl(url)
                .restClientBuilder(RestClient.builder().requestFactory(factory)).build();
        // Spring AI's default model management does not pull models automatically.
        return OllamaEmbeddingModel.builder().ollamaApi(api)
                .options(OllamaEmbeddingOptions.builder().model(model).build()).build();
    }
}
