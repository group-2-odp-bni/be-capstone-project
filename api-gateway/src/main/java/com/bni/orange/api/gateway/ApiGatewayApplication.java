package com.bni.orange.api.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.management.ManagementFactory;

@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        var runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        System.out.println(">>> JVM Args: " + runtimeMxBean.getInputArguments());
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

}
