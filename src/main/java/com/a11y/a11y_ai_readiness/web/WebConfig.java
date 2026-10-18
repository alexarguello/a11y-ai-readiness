package com.a11y.a11y_ai_readiness.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the results/ directory (agent runs, axe-core baseline, the generated dashboard) so the
 * running app can link straight to results/dashboard.html instead of that file only being
 * reachable by opening it directly on disk.
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/results/**")
                .addResourceLocations("file:results/");
    }
}
