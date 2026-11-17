package com.cantill.procurement.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.net.URI;

@Configuration
public class DataSourceConfig {
    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new CustomDataSourceProperties();
    }

    public static class CustomDataSourceProperties extends DataSourceProperties {
        private String parsedUrl;
        private String extractedUsername;
        private String extractedPassword;

        @Override
        public String getUrl() {
            String rawUrl = super.getUrl();
            if (rawUrl == null) {
                return null;
            }
            if (parsedUrl != null) {
                return parsedUrl;
            }

            rawUrl = rawUrl.trim();
            if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
                try {
                    URI uri = URI.create(rawUrl);
                    String userInfo = uri.getUserInfo();
                    if (userInfo != null && !userInfo.isEmpty()) {
                        String[] parts = userInfo.split(":", 2);
                        this.extractedUsername = parts[0];
                        if (parts.length > 1) {
                            this.extractedPassword = parts[1];
                        }
                    }

                    int port = uri.getPort();
                    String portPart = (port > 0) ? ":" + port : "";
                    String path = uri.getPath() != null ? uri.getPath() : "";
                    String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";

                    parsedUrl = "jdbc:postgresql://" + uri.getHost() + portPart + path + query;
                    log.info("Normalized Neon database URL for host: {}{}", uri.getHost(), path);
                    return parsedUrl;
                } catch (Exception e) {
                    log.warn("Could not parse URI structure, falling back to prefix replacement: {}", e.getMessage());
                    if (rawUrl.startsWith("postgres://")) {
                        parsedUrl = "jdbc:postgresql://" + rawUrl.substring("postgres://".length());
                    } else {
                        parsedUrl = "jdbc:postgresql://" + rawUrl.substring("postgresql://".length());
                    }
                    return parsedUrl;
                }
            }
            return rawUrl;
        }

        @Override
        public String getUsername() {
            String user = super.getUsername();
            if ((user == null || user.isBlank()) && extractedUsername != null) {
                return extractedUsername;
            }
            return user;
        }

        @Override
        public String getPassword() {
            String pass = super.getPassword();
            if ((pass == null || pass.isBlank()) && extractedPassword != null) {
                return extractedPassword;
            }
            return pass;
        }
    }
}
