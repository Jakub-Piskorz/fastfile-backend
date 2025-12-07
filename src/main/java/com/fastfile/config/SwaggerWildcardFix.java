package com.fastfile.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerWildcardFix {

    @Bean
    public OpenApiCustomizer fixWildcardPaths() {
        return openApi -> {
            var paths = openApi.getPaths();

            // 1. collect keys to fix (no modification yet)
            var toFix = paths.keySet().stream()
                    .filter(k -> k.contains("{*path}"))
                    .toList();

            // 2. apply modifications safely
            for (var oldKey : toFix) {
                var newKey = oldKey.replace("{*path}", "{path}");
                var item = paths.remove(oldKey); // safe now
                paths.addPathItem(newKey, item);
            }
        };
    }
}