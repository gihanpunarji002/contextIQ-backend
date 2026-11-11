package com.gihan.contextioq.ContextIQ.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.retry.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration @ConditionalOnProperty(name="contextiq.ai.mode",havingValue="OLLAMA",matchIfMissing=true)
public class OllamaConfig {
 @Bean public ChatModel ollamaChatModel(@Value("${contextiq.ai.base-url}")
                                        String url,@Value("${contextiq.ai.model}")
 String model,@Value("${contextiq.ai.max-output-tokens}")
 int maxTokens,@Value("${contextiq.ai.context-size}") int contextSize){
  if(model.isBlank()||model.length()>120||maxTokens<16||maxTokens>8192||contextSize<2048||contextSize>131072)
   throw new IllegalArgumentException("Invalid Ollama model or token limits");


  var uri=java.net.URI.create(url);

  if(!java.util.Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null)
   throw new IllegalArgumentException("OLLAMA_BASE_URL must be an HTTP(S) URL");
  var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build());
  factory.setReadTimeout(Duration.ofSeconds(120));
  var api=OllamaApi.builder().baseUrl(url).restClientBuilder(RestClient.builder().requestFactory(factory)).build();
  // Explicit configuration avoids auto-pulling models and keeps HTTP retries disabled.
  return OllamaChatModel.builder().ollamaApi(api)
   .options(OllamaChatOptions.builder().model(model).numPredict(maxTokens).numCtx(contextSize).disableThinking().build())
   .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0))).build();
 }
}
