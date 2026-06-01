# ShipRate API

A Spring Boot REST API simulating enterprise shipping rating logic — base rates, fuel surcharges, and remote-area fees — built with a layered Controller–Service–Repository architecture. Designed to demonstrate production-grade Java/Spring Boot patterns including JWT auth, Redis caching, and containerized deployment.

## Tech Stack

- Java 17
- Spring Boot
- Spring Web
- Maven
- Spring Boot Actuator

## API Endpoints

### Health Check

```http
GET /api/health