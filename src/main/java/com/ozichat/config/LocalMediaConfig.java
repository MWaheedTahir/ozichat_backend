package com.ozichat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
@Profile("dev")
public class LocalMediaConfig implements WebMvcConfigurer {

    @Value("${media.local.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absPath = Paths.get(uploadDir).toAbsolutePath().normalize().toString();
        registry.addResourceHandler("/media/**")
                .addResourceLocations("file:" + absPath + "/");
    }
}