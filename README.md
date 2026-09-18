# OCPP Central System

Spring Boot backend that acts as an OCPP 1.6 central system: it talks to EV charge points over
WebSocket and exposes a REST API to monitor and control them.

Built on Java 25, Spring Boot 3.5 and [Java-OCA-OCPP](https://github.com/ChargeTimeEU/Java-OCA-OCPP).

---

## Features

* OCPP 1.6 over WebSocket: authorize, boot notification, heartbeat, status notification, meter values, start and stop transaction
* Charge point registry with remote operations (reset, connector unlock, configuration change, trigger messages)
* Tag registry for authorization (RFID / APP / REMOTE) with customer details, expiry dates and blocking
* Transaction lifecycle, from remote start through meter values to stop
* REST API with a consistent error format, documented with OpenAPI / Swagger
* H2 in-memory database, so it runs with no external setup

---

## Status

* Under active development.
* The API is unauthenticated, so remote operations such as reset and unlock are open to anyone who can reach the port.
* WebSocket resilience (reconnects, pings, error recovery) still needs work.
* Test coverage is thin; remote operations are verified against a mocked OCPP transport, so there is no
  end-to-end test with a real charge point yet.

---

## Run

Requires Java 25 and Maven 3.9+.

```bash
mvn spring-boot:run
```

Or with Docker:

```bash
docker compose up --build
```

The REST API listens on port 7070 and the OCPP WebSocket endpoint on 8080, so charge points connect
to `ws://<host>:8080/OCPP16/<id>`. For hosts that expose a single port, activate the `single-port`
profile.

API reference: <http://localhost:7070/swagger-ui/index.html> (H2 console: <http://localhost:7070/h2-console>)

Tags live in the database, so register one through the tag API before a charge point can authorize -
unknown, blocked and expired tags are rejected.

---

## Tests

```bash
mvn test
```

---

## License

MIT - see [LICENSE](LICENSE).

---

## Contributions

Contributions are welcome: fork the repository, open an issue, or submit a pull request.
