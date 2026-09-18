package com.ocppcentralsystem.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Publishes the {@link TenantResolver#TENANT_HEADER} header of every request to the thread handling
 * it, which is what lets an MCP tool work in the tenant of the client that called it.
 *
 * <p>Applies to every path, so it also covers endpoints that are served outside Spring MVC - the
 * MCP transport, for one, is a servlet of its own.</p>
 *
 * <p>The header is kept raw here and never validated: an unusable value has to be reported by the
 * normal error handling, and a throw from a filter would happen before Spring MVC could render
 * anything.</p>
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        TenantContext.set(request.getHeader(TenantResolver.TENANT_HEADER));
        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
