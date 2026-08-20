# 📁 FastFile

FastFile is a Spring Boot-based cloud file storage backend that supports file upload, download, directory browsing, and user authentication with JWT.

---

## 🚀 Features

- Upload and download files
- List and search files in directories
- Create and delete directories
- Register and authenticate users
- Get authenticated user details via JWT
- PostgreSQL and JPA integration
- Docker Compose ready
- Flyway for DB migrations

---

## 🧱 Tech Stack

- Java 17
- Spring Boot 3.3.0
- Spring Security + JWT
- PostgreSQL + JPA (Hibernate)
- Flyway for migrations
- Docker Compose (optional)
- Maven

---

## 📦 Endpoints

### 🔐 Auth

| Method | Endpoint                   | Description                          |
|--------|----------------------------|--------------------------------------|
| POST   | `/auth/register`           | Register a new user                  |
| POST   | `/auth/login`              | Authenticate and receive JWT         |
| GET    | `/auth/user`               | Get current user by token            |
| POST   | `/auth/user/set-user-type` | Set user type to "free" or "premium" |

### 📁 File Operations

| Method | Endpoint                              | Description                    |
|--------|---------------------------------------|--------------------------------|
| GET    | `/api/v1/files/list/**`               | List files in a directory      |
| GET    | `/api/v1/files/search/**`             | Search files by name           |
| GET    | `/api/v1/files/download/**`           | Download a file                |
| POST   | `/api/v1/files/upload`                | Upload a file (multipart)      |
| DELETE | `/api/v1/files/delete`                | Delete a file                  |
| POST   | `/api/v1/files/create-directory/**`   | Create a new directory         |

---

## 📅 File Upload Format

**POST** `/api/v1/files/upload`

Form data:
- `file`: The file to upload
- `filePath`: Relative path where file should be stored

---

## 🔐 Auth Flow (JWT)

1. Register via `/auth/register`
2. Log in via `/auth/login` to receive a JWT token
3. Access protected endpoints with:

```
Authorization: Bearer <your_jwt_token>
```

---

## ⚙️ Build & Run

### Build

```bash
mvn clean package
```

### Run Dev (development mode with HTTP)

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=dev -f pom.xml
```

### Run Prod (production mode with HTTPS)

```bash
./mvnw spring-boot:run
```

---

## 🐳 Docker

If you're using Docker Compose:

```bash
docker-compose up --build
```

---

## 🛠️ Environment Variables

This project uses certain environment variables.
Be sure to add and configure those below, before running the project:
- `var_dbUsername`: Username for your PostgreSQL database
- `var_dbPassword`: Password for your PostgreSQL database
- `var_keystorePass`: Password for SSL keystore (necessary for hosting via HTTPS)

Used only in Integration tests:
- `var_ffUsername`: Username in database used for tests
- `var_ffPassword`: Password in database used for tests

---

## 🛠️ Configuring HTTPS

Put `keystore.p12` file for PROD and `keystore-local.p12` for DEV, inside `src/main/resources/` folder and set up your `var_keystorePass` environment variable for keystore password.
For

