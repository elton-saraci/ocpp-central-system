package com.ocppcentralsystem.tenant;

import lombok.AllArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Fills {@link TenantId} parameters, so a controller declares the tenant it works in and never has
 * to reach for the request itself.
 */
@Component
@AllArgsConstructor
public class TenantArgumentResolver implements HandlerMethodArgumentResolver {

    private final TenantResolver tenantResolver;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(TenantId.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        // The header was captured by TenantFilter, so this is the same value for every consumer.
        return tenantResolver.current();
    }
}
