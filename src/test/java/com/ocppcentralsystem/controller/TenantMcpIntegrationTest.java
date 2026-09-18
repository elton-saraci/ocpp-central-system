package com.ocppcentralsystem.controller;

import com.ocppcentralsystem.model.ChargePointRequest;
import com.ocppcentralsystem.service.ChargePointRegistryService;
import com.ocppcentralsystem.tenant.TenantResolver;
import eu.chargetime.ocpp.JSONServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Drives the MCP endpoint over real HTTP, because that is where the tenant of an MCP tool call
 * comes from: the {@link TenantResolver#TENANT_HEADER} header of the client's request, published to
 * the thread that runs the tool. A controller-style test would not prove that the header survives
 * the MCP transport.
 *
 * <p>There is no {@code @Transactional} here on purpose: the tool runs on the server's own thread
 * and only sees committed data.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantMcpIntegrationTest {

    private static final String ACME = "acme-mcp";
    private static final String BETA = "beta-mcp";

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ChargePointRegistryService registry;

    @MockitoBean
    private JSONServer jsonServer;

    @Test
    void aToolCallActsForTheTenantOfTheRequestHeader() {
        registry.createStation(ACME, request(ACME + "/CP-1"));

        assertTrue(listChargePoints(ACME).contains(ACME + "/CP-1"),
                "the tool should list the station of the tenant the header named");

        assertFalse(listChargePoints(BETA).contains(ACME + "/CP-1"),
                "another tenant must not see it");
    }

    @Test
    void aToolCallWithoutTheHeaderWorksInTheDefaultTenant() {
        // A station of its own: this class cannot roll back, so both tests would otherwise share one.
        registry.createStation(ACME, request(ACME + "/CP-2"));

        String withoutHeader = listChargePoints(null);

        assertFalse(withoutHeader.contains(ACME + "/CP-2"),
                "without a header the default tenant applies, which is not where the station is");
        assertTrue(withoutHeader.contains("\"isError\":false"),
                "the call itself still succeeds: " + withoutHeader);
        assertTrue(withoutHeader.contains("\"text\":\"[]\""),
                "and the default tenant has no stations to list: " + withoutHeader);
    }

    /** Calls {@code ocppListChargePoints} over MCP JSON-RPC, naming a tenant or not. */
    private String listChargePoints(String tenant) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_JSON);
        headers.set(HttpHeaders.ACCEPT, "application/json, text/event-stream");
        if (tenant != null) {
            headers.set(TenantResolver.TENANT_HEADER, tenant);
        }

        String body = """
                {"jsonrpc":"2.0","id":1,"method":"tools/call",
                 "params":{"name":"ocppListChargePoints","arguments":{}}}
                """;

        return restTemplate.postForObject("/mcp", new HttpEntity<>(body, headers), String.class);
    }

    private static ChargePointRequest request(String cpId) {
        ChargePointRequest request = new ChargePointRequest();
        request.setCpId(cpId);
        return request;
    }
}
