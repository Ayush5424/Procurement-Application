package com.cantill.procurement.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000,http://localhost:8080}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // Never intercept any REST API endpoints
                        if (resourcePath.startsWith("api") || resourcePath.startsWith("/api")) {
                            return null;
                        }

                        // Root requests return index.html
                        if (resourcePath.isEmpty() || resourcePath.equals("/") || resourcePath.equals("index.html")) {
                            return new ClassPathResource("/static/index.html");
                        }

                        Resource requestedResource = location.createRelative(resourcePath);
                        if (requestedResource.exists() && requestedResource.isReadable()) {
                            return requestedResource;
                        }

                        // Missing files with extensions (.js, .css, .png, etc.) return null (404)
                        if (resourcePath.contains(".") && !resourcePath.endsWith(".html")) {
                            return null;
                        }

                        // Client-side SPA routes (e.g. /buyer, /chat, /policy) forward to index.html
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
