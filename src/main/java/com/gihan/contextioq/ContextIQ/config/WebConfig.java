package com.gihan.contextioq.ContextIQ.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String origin;

  public WebConfig(@Value("${contextiq.frontend-url}") String origin) {
    this.origin = origin;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
      .addMapping("/api/**")
      .allowedOrigins(origin)
      .allowedMethods("GET", "POST", "DELETE")
      .allowedHeaders("Content-Type", "X-CSRF-TOKEN")
      .allowCredentials(true)
      .maxAge(3600);
  }
}
