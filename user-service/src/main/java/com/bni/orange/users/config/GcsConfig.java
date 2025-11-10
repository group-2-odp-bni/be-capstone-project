package com.bni.orange.users.config;

import com.bni.orange.users.config.properties.GcsProperties;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(GcsProperties.class)
public class GcsConfig {

    @Bean
    public Storage storage() {
        log.info("Initializing Google Cloud Storage with Application Default Credentials");
        return StorageOptions.getDefaultInstance().getService();
    }
}
