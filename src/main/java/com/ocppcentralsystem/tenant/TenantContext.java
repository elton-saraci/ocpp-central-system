package com.ocppcentralsystem.tenant;

/**
 * The tenant a request named, for the duration of that request on this thread.
 *
 * <p>Set by {@link TenantFilter} and cleared again in a finally block, so a pooled thread never
 * carries one request's tenant into the next.</p>
 *
 * <p>It exists for the calls that cannot declare the tenant themselves: an MCP tool is invoked by
 * the MCP transport rather than by a controller, so it has no {@code @TenantId} parameter to
 * fill. Controllers do not need it, they declare the parameter.</p>
 */
final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** @return the header value the request carried, or {@code null} when it carried none. */
    static String get() {
        return CURRENT.get();
    }

    static void set(String tenant) {
        CURRENT.set(tenant);
    }

    static void clear() {
        CURRENT.remove();
    }
}
