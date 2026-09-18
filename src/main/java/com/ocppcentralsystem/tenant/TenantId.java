package com.ocppcentralsystem.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the controller parameter that receives the tenant of the request.
 *
 * <p>The value comes from the {@link TenantResolver#TENANT_HEADER} header, or from the configured
 * default tenant when the header is absent. It is never null, so controller code can use it
 * without a null check.</p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantId {
}
