# OCPP Central System

Spring Boot backend that acts as an OCPP 1.6 central system: it talks to EV charge points over
WebSocket and exposes a REST API to monitor and control them.

Built on Java 25, Spring Boot 3.5 and [Java-OCA-OCPP](https://github.com/ChargeTimeEU/Java-OCA-OCPP).

---

## Features

* OCPP 1.6 over WebSocket: authorize, boot notification, heartbeat, status notification, meter values, start and stop transaction
* Station registry (CRUD) holding identity, connectors, wiring and free-form metadata - only a registered, enabled station is allowed to connect
* Data separation per tenant: an `X-Tenant-Id` header scopes every request, and a station's session acts for the tenant of that station
* Remote operations on a connected station (reset, connector unlock, configuration change, trigger messages)
* Smart charging: set, clear and read back the power limit of a charge point or a single connector, in watts
* Tag registry for authorization (RFID / APP / REMOTE) with customer details, expiry dates and blocking
* Transaction lifecycle, from remote start through meter values to stop
* REST API with a consistent error format, documented with OpenAPI / Swagger
* H2 in-memory database, so it runs with no external setup

---

## Status

* Under active development.
* The API is unauthenticated, so remote operations such as reset and unlock are open to anyone who can reach the port.
* Tenants are a data boundary, not a security one: the header is not authenticated, so any caller can name any tenant today.
* WebSocket resilience (reconnects, pings, error recovery) still needs work.
* Remote operations are verified against a mocked OCPP transport, so there is no
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
profile. The `OCPP16` segment is optional (`OCPP1.6` is accepted too) and a query string is ignored;
everything after it is the station's identity, slashes included.

API reference: <http://localhost:7070/swagger-ui/index.html> (H2 console: <http://localhost:7070/h2-console>)

Stations and tags live in the database: a station must be registered - and enabled - before it may
connect, and a tag must be registered before a charge point can authorize with it. Unknown, blocked
and expired tags are rejected.

Rows are separated per tenant: name yours in an `X-Tenant-Id` header, or leave it out to work in the
`default` tenant (`tenant.default`). The header applies to every request, MCP included, so an MCP
client sets it once and all its tool calls act in that tenant. A charge point cannot send headers, so
its session acts for the tenant of the station it connected as. That is also why a station id may be
namespaced and contain slashes, e.g. `ws://<host>:8080/OCPP16/acme/depot/CP-1`; station ids in the
API are query parameters for the same reason.

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
