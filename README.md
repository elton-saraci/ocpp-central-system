# OCPP Central System

**OCPP Central System** is an open-source, Spring Boot-based backend service designed to manage EV charge stations via WebSocket communication, compliant with the [OCPP 1.6 protocol](https://www.openchargealliance.org/protocols/ocpp-16/).  
This service acts as a central system to communicate with charge points, process transaction data, and monitor station status in real-time.

---

## ⚙️ Features

- **WebSocket support for OCPP 1.6** using [Java-OCA-OCPP](https://github.com/ChargeTimeEU/Java-OCA-OCPP)
- **Charge Point Management**
- **Transaction Lifecycle Management** (Start / Stop / MeterValues)
- **Authorization and Heartbeat Handling**
- **BootNotification and StatusNotification Processing**
- **REST API for Charge Points and Transactions**
- **H2 in-memory database** for quick startup and testing
- **OpenAPI (Swagger UI)** integration for API documentation

---

## 📦 Technologies Used

- Java 17
- Spring Boot 3.2
- Java-OCA-OCPP
- Spring Web / Spring Data JPA
- H2 Database
- Lombok
- MapStruct
- OpenAPI (springdoc-openapi)

---

## 📁 Project Structure

```
src/
├── main/
│   ├── java/com/ocppcentralsystem/
│   │   ├── controller/               # REST controllers
│   │   ├── config/                   # OCPP event handler + profiles
│   │   ├── factory/                  # Helper factories for confirmation and transaction logic
│   │   ├── model/                    # Entity and DTO classes
│   │   ├── repository/               # Spring Data repositories
│   │   ├── service/                  # Business logic for ChargePoints and Transactions
│   │   └── util/                     # Utilities (e.g. parsing MeterValues)
│   └── resources/
│       └── application.yml           # Configuration file
```

---

## 📱 WebSocket Integration

The OCPP 1.6 protocol is handled via `ServerCoreEventHandler`, which maps all core OCPP messages such as:

- `AuthorizeRequest`
- `BootNotificationRequest`
- `StartTransactionRequest`
- `StopTransactionRequest`
- `MeterValuesRequest`
- `StatusNotificationRequest`
- `HeartbeatRequest`

This handler is exposed as a Spring `@Bean` and registered via the `ServerCoreProfile`.

---

## 🔌 REST API Endpoints

| Endpoint                             | Method | Description                         |
|--------------------------------------|--------|-------------------------------------|
| `/charge-point`                      | GET    | List all registered charge points   |
| `/charge-transaction`               | GET    | List all transactions               |
| `/charge-transaction/{id}`          | GET    | Get transaction by ID               |
| `/charge-transaction/start`         | POST   | Start a new charging session        |
| `/charge-transaction/stop/{id}`     | POST   | Stop an existing transaction        |

Full API documentation is available via Swagger UI.

---

## ▶️ Getting Started

### Prerequisites

- Java 17+
- Maven 3+

### Run the application

```bash
mvn spring-boot:run
```

### Access the APIs (based on ports you set on application.yml)

- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- H2 Console (if enabled): [http://localhost:8080/h2-console](http://localhost:8080/h2-console)

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
