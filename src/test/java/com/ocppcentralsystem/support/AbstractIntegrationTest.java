package com.ocppcentralsystem.support;

import eu.chargetime.ocpp.JSONServer;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared setup for the tests that run against the real Spring context and roll back afterwards.
 *
 * <p>The annotations live here rather than on each test class because the context cache keys on the
 * merged test configuration: subclasses that inherit exactly these share one context, and a class that
 * quietly differs would start a second one - or, worse, lose the shared one without anyone noticing.</p>
 *
 * <p>Mocking {@link JSONServer} also keeps the suite from binding the OCPP port: the real server tries
 * to listen on 8080, which a development instance usually already holds.</p>
 */
@SpringBootTest
@Transactional
public abstract class AbstractIntegrationTest {

    /** The tenant the tests work in: the configured default one. */
    protected static final String TENANT = TestTenants.DEFAULT;

    @Autowired
    protected EntityManager entityManager;

    @MockitoBean
    protected JSONServer jsonServer;

    /**
     * Sends what the session has pending to the database and detaches it, so a test can read back what
     * a bulk update or a flush-triggering call actually wrote.
     */
    protected void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
