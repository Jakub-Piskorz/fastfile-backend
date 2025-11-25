package com.fastfile.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FilesConfig {

    @Value("${files.root-dir}")
    private String injectedValue;

    public static String FILES_ROOT;

    @PostConstruct
    void init() {
        FILES_ROOT = injectedValue;
    }
}
