package com.ocppcentralsystem.support;

/**
 * The tenant the tests work in.
 *
 * <p>{@link #DEFAULT} is the configured default tenant, so a test that sends no header and a test
 * that calls a service directly land in the same data. Tests that prove separation use a tenant of
 * their own, e.g. {@code TenantIsolationIntegrationTest}.</p>
 */
public final class TestTenants {

    public static final String DEFAULT = "default";

    private TestTenants() {
    }
}
