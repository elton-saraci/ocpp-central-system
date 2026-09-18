package com.ocppcentralsystem.tenant;

import com.ocppcentralsystem.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Decides which tenant a caller belongs to.
 *
 * <p>HTTP callers name their tenant in the {@link #TENANT_HEADER} header. There is no
 * authentication yet, so this is a scoping hint rather than a security boundary: it decides which
 * rows a request reads and which tenant new rows are stamped with, and nothing more.</p>
 *
 * <p>A missing header falls back to the configured default tenant, which keeps a single tenant
 * deployment working without every caller having to send one. OCPP sessions cannot send headers,
 * so they get their tenant from the registered station instead.</p>
 */
@Component
public class TenantResolver {

    /** Header a caller names its tenant in, e.g. {@code X-Tenant-Id: acme}. */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * The tenant is part of every query, so it is kept to something that reads as a plain key. A
     * station id may contain slashes; a tenant may not, which keeps the two from being confused.
     */
    private static final Pattern VALID_TENANT = Pattern.compile("[A-Za-z0-9._-]{1,50}");

    private final String defaultTenant;

    public TenantResolver(@Value("${tenant.default:default}") String defaultTenant) {
        this.defaultTenant = defaultTenant.trim();
    }

    /**
     * @param tenant the value a caller supplied, e.g. from the header or an MCP tool argument
     * @return the tenant to scope the work to, never blank
     * @throws InvalidRequestException when the value cannot be used as a tenant.
     */
    public String resolve(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            return defaultTenant;
        }

        String candidate = tenant.trim();
        if (!VALID_TENANT.matcher(candidate).matches()) {
            throw new InvalidRequestException("Tenant is not a usable identifier",
                    List.of("tenant must be 1-50 characters of letters, digits, dot, dash or underscore"));
        }
        return candidate;
    }

    /**
     * The tenant of the request being handled on this thread.
     *
     * @return what the request named in {@link #TENANT_HEADER}, or the configured default tenant
     *         when it named none - which is also the answer outside a request, e.g. in a test that
     *         calls a service directly.
     */
    public String current() {
        return resolve(TenantContext.get());
    }
}
