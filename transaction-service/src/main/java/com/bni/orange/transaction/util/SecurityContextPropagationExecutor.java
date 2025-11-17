package com.bni.orange.transaction.util;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class SecurityContextPropagationExecutor {

    private final Executor virtualThreadTaskExecutor;

    public <T> CompletableFuture<T> supplyAsyncWithContext(Supplier<T> supplier) {
        SecurityContext context = SecurityContextHolder.getContext();
        return CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.setContext(context);
            try {
                return supplier.get();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, virtualThreadTaskExecutor);
    }

    public CompletableFuture<Void> runAsyncWithContext(Runnable runnable) {
        SecurityContext context = SecurityContextHolder.getContext();
        return CompletableFuture.runAsync(() -> {
            SecurityContextHolder.setContext(context);
            try {
                runnable.run();
            } finally {
                SecurityContextHolder.clearContext();
            }
        }, virtualThreadTaskExecutor);
    }
}
