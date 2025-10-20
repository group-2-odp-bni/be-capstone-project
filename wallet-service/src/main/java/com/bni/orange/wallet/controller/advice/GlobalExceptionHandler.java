// controller/advice/GlobalExceptionHandler.java
package com.bni.orange.wallet.controller.advice;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.model.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiResponse<Object>> handleApi(ApiException ex) {
    return ResponseEntity.status(ex.getStatus()).body(ApiResponse.err(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Object>> handleMethodArg(MethodArgumentNotValidException ex) {
    var msg = ex.getBindingResult().getFieldErrors().stream()
        .findFirst().map(fe -> fe.getField()+" "+fe.getDefaultMessage())
        .orElse("Validation error");
    return ResponseEntity.badRequest().body(ApiResponse.err(msg));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiResponse<Object>> handleConstraint(ConstraintViolationException ex) {
    var msg = ex.getConstraintViolations().stream().findFirst()
        .map(v -> v.getPropertyPath()+": "+v.getMessage()).orElse("Validation error");
    return ResponseEntity.badRequest().body(ApiResponse.err(msg));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Object>> handleConflict(DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.err("Data conflict"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
    log.error("Unhandled error at {}:", ex); 
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.err("Internal error"));
  }
}
