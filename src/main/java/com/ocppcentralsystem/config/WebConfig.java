package com.ocppcentralsystem.config;

import com.ocppcentralsystem.tenant.TenantArgumentResolver;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@AllArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TenantArgumentResolver tenantArgumentResolver;

    /** Teaches Spring to fill {@code @TenantId} parameters from the request header. */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(tenantArgumentResolver);
    }
}
