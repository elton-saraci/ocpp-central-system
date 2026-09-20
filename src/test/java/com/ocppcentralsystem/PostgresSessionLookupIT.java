package com.ocppcentralsystem;

import com.ocppcentralsystem.config.JsonServerImpl;
import com.ocppcentralsystem.model.ChargePoint;
import com.ocppcentralsystem.model.ChargeTransaction;
import com.ocppcentralsystem.model.Tag;
import com.ocppcentralsystem.model.TagStatus;
import com.ocppcentralsystem.model.TagType;
import com.ocppcentralsystem.model.WebsocketConnectionStatus;
import com.ocppcentralsystem.repository.ChargePointRepository;
import com.ocppcentralsystem.repository.ChargeTransactionRepository;
import com.ocppcentralsystem.repository.TagRepository;
import eu.chargetime.ocpp.JSONServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs every statement that touches {@code charge_point.websocket_id} against a real Postgres.
 *
 * <p>H2 converts between varchar and uuid on its own, so the default suite stays green while
 * Postgres rejects the very same query with {@code operator does not exist: character varying =
 * uuid} - which is how a release took every charge point offline. This is the guard for that class
 * of bug: the column type and the bind type have to agree.</p>
 *
 * <p>Named {@code *IT} so surefire's default includes skip it. Run it by hand:</p>
 *
 * <pre>
 * docker run -d --name ocpp-pg-check -e POSTGRES_USER=ocpp -e POSTGRES_PASSWORD=ocpp \
 *   -e POSTGRES_DB=ocpp -p 55432:5432 postgres:17-alpine
 * SPRING_PROFILES_ACTIVE=postgres SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/ocpp \
 *   SPRING_DATASOURCE_USERNAME=ocpp SPRING_DATASOURCE_PASSWORD=ocpp \
 *   mvn clean test -Dtest=PostgresSessionLookupIT
 * docker rm -f ocpp-pg-check
 * </pre>
 *
 * <p>Rolls back, so it leaves nothing behind and can be pointed at an existing database too.</p>
 */
@SpringBootTest
@ActiveProfiles("postgres")
@Transactional
class PostgresSessionLookupIT {

    @MockitoBean
    private JSONServer jsonServer;

    /** Mocked so the test context does not open the OCPP port. */
    @MockitoBean
    private JsonServerImpl jsonServerImpl;

    @Autowired
    private ChargePointRepository chargePointRepository;
    @Autowired
    private ChargeTransactionRepository chargeTransactionRepository;
    @Autowired
    private TagRepository tagRepository;

    @Test
    void everySessionLookupWorksOnPostgres() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID websocketId = UUID.randomUUID();

        ChargePoint station = chargePointRepository.saveAndFlush(ChargePoint.builder()
                .tenant("default")
                .cpId("CP-PG-" + suffix)
                .websocketId(websocketId)
                .connectionStatus(WebsocketConnectionStatus.OPEN)
                .enabled(true)
                .build());

        Optional<ChargePoint> found = chargePointRepository.findByWebsocketId(websocketId);
        assertTrue(found.isPresent(), "the session must be found by its websocket id");
        assertEquals(websocketId, found.get().getWebsocketId(), "the handle must survive the round trip");

        String idTag = "TAG-" + suffix;
        Tag tag = tagRepository.saveAndFlush(Tag.builder()
                .idTag(idTag)
                .tenant("default")
                .customerName("Postgres check")
                .tagType(TagType.RFID)
                .status(TagStatus.ACTIVE)
                .build());

        ChargeTransaction transaction = chargeTransactionRepository.saveAndFlush(
                new ChargeTransaction(station, 1, tag));

        assertEquals(transaction.getChargeTransactionId(),
                chargeTransactionRepository.findLatestByWebsocketIdAndIdTag(websocketId, idTag)
                        .orElseThrow(() -> new AssertionError("the running transaction must be found"))
                        .getChargeTransactionId());
        assertEquals(websocketId, chargeTransactionRepository
                .findChargePointWebsocketId(transaction.getChargeTransactionId()).orElseThrow());
    }
}
