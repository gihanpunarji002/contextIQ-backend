package com.gihan.contextioq.ContextIQ.common.exception;

import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(
    ApiExceptionHandler.class
  );

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail status(ResponseStatusException e) {
    return ProblemDetail.forStatusAndDetail(
      e.getStatusCode(),
      e.getReason() == null ? "Request failed" : e.getReason()
    );
  }

  @ExceptionHandler(
    org.springframework.security.authentication.BadCredentialsException.class
  )
  public ProblemDetail credentials(Exception e) {
    return ProblemDetail.forStatusAndDetail(
      HttpStatus.UNAUTHORIZED,
      "Invalid email or password"
    );
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail unexpected(Exception e) {
    log.error("Unexpected API failure", e);
    return ProblemDetail.forStatusAndDetail(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "An unexpected error occurred."
    );
  }
}
