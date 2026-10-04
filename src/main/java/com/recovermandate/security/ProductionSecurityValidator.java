package com.recovermandate.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionSecurityValidator implements ApplicationRunner {

    @Value("${recovermandate.security.api-key:}")
    private String apiKey;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (apiKey == null || apiKey.isBlank() || "default-dev-key".equals(apiKey)) {
            throw new IllegalStateException("Production deployment cannot start with a missing or default API_KEY");
        }
        if (dbPassword == null || dbPassword.isBlank() || "postgres".equals(dbPassword)) {
            throw new IllegalStateException("Production deployment cannot start with a missing or default DB_PASSWORD");
        }
    }
}
