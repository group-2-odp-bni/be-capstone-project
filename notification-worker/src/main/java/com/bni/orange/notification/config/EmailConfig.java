package com.bni.orange.notification.config;

import com.bni.orange.notification.config.properties.EmailConfigProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(EmailConfigProperties.class)
public class EmailConfig {
}
