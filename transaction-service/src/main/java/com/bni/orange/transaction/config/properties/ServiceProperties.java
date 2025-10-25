package com.bni.orange.transaction.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "orange.services")
public class ServiceProperties {

    private UserServiceDetail userService;
    private ServiceDetail walletService;
    private ServiceDetail authenticationService;

    @Data
    public static class UserServiceDetail {
        private String url;
        private int timeout = 5000;
    }

    @Data
    public static class ServiceDetail {
        private String url;
    }
}
