package com.bni.orange.wallet.model.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiResponse<T> {
  private boolean error;  
  private String message; 
  private T data;     
  
  public static <T> ApiResponse<T> ok(String message, T data) {
    return ApiResponse.<T>builder().error(false).message(message).data(data).build();
  }
  public static <T> ApiResponse<T> err(String message) {
    return ApiResponse.<T>builder().error(true).message(message).data(null).build();
  }
}
