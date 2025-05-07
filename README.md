# OCPP Central System

**OCPP Central System** is an open-source, Spring Boot-based backend service designed to manage EV charge stations via WebSocket communication, compliant with the [OCPP 1.6 protocol](https://www.openchargealliance.org/protocols/ocpp-16/).
This service acts as a central system to communicate with charge points, process transaction data, and monitor station status in real-time.

---

## ⚙️ Features

* **WebSocket support for OCPP 1.6** using [Java-OCA-OCPP](https://github.com/ChargeTimeEU/Java-OCA-OCPP)
* **Charge Point Management** with remote trigger capabilities (reset, unlock, status notifications, heartbeat, meter values, configuration change)
* **Transaction Lifecycle Management** (Start / Stop / MeterValues)
* **Authorization and Heartbeat Handling**
* **BootNotification and StatusNotification Processing**
* **REST API for Charge Points and Transactions**
* **H2 in-memory database** for quick startup and testing
* **OpenAPI (Swagger UI)** integration for API documentation
* **Docker and Docker Compose support** for simplified containerized deployment

---

## 📦 Technologies Used

* Java 17
* Spring Boot 3.2
* Java-OCA-OCPP
* Spring Web / Spring Data JPA
* H2 Database
* Lombok
* MapStruct
* OpenAPI (springdoc-openapi)
* Docker

---

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/ocppcentralsystem/
│   │   ├── config/                   # OCPP event handler, JSON server, application config
│   │   ├── controller/               # REST controllers
│   │   ├── factory/                  # Confirmation and transaction creation helpers
│   │   ├── mapper/                   # DTO mappers (e.g. MapStruct)
│   │   ├── model/                    # Entities, DTOs, Enums
│   │   ├── repository/               # Spring Data JPA repositories
│   │   ├── service/                  # Business logic layer
│   │   └── util/                     # Utility classes (e.g. meter value parsing)
│   └── resources/
│       └── application.yml           # Configuration file
Dockerfile                            # Docker build definition
docker-compose.yml                    # Multi-container orchestration
```

---

## 🔌 REST API Endpoints

| Endpoint                                     | Method | Description                                 |
| -------------------------------------------- | ------ | ------------------------------------------- |
| `/charge-point`                              | GET    | List all registered charge points           |
| `/charge-point/{cpId}/hard-reset`            | POST   | Trigger a hard reset on charge point        |
| `/charge-point/{cpId}/soft-reset`            | POST   | Trigger a soft reset on charge point        |
| `/charge-point/{cpId}/connector-unlock`      | POST   | Unlock a specific connector on charge point |
| `/charge-point/{cpId}/status-notification`   | POST   | Trigger status notification request         |
| `/charge-point/{cpId}/boot-notification`     | POST   | Trigger boot notification request           |
| `/charge-point/{cpId}/heartbeat`             | POST   | Trigger heartbeat request                   |
| `/charge-point/{cpId}/meter-values`          | POST   | Trigger meter values request                |
| `/charge-point/{cpId}/change-configurations` | POST   | Change configuration key on charge point    |
| `/charge-transaction`                        | GET    | List all transactions                       |
| `/charge-transaction/{id}`                   | GET    | Get transaction by ID                       |
| `/charge-transaction/start`                  | POST   | Start a new charging session                |
| `/charge-transaction/stop/{id}`              | POST   | Stop an existing transaction                |

---

## ⚠️ Known Limitations

* Error handling is currently minimal and not standardized.
* API responses do not yet follow a consistent structure (e.g. error codes or response envelope).
* WebSocket resilience (e.g. reconnections, pings, error recovery) needs enhancement.
* This project is under active development.

Community contributions to improve error handling, response structure, and WebSocket robustness are highly welcome!

---

## ▶️ Getting Started

### Prerequisites

* Java 17+
* Maven 3+

### Run the application

```bash
mvn spring-boot:run
```

### Or run via Docker

```bash
docker-compose up --build
```

### Access the APIs

* Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* H2 Console (if enabled): [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

---

## 🥪 Testing

Run all unit tests using:

```bash
mvn test
```

---

## 📄 License

This project is open-sourced under the [MIT License](LICENSE).

---

## 🤝 Contribution

Contributions are welcome! Please fork the repository, open an issue, or submit a pull request.

---

## 🌐 Repository Origin

> This project builds on and integrates [ChargeTime's Java-OCA-OCPP](https://github.com/ChargeTimeEU/Java-OCA-OCPP) library for OCPP 1.6 protocol support.
