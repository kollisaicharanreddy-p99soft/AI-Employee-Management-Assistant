# Employee Management System

A RESTful API built with **Spring Boot 3** and **JDK 21** for managing employees and departments.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.3.4 |
| Language | Java 21 |
| Database | H2 (in-memory, dev) |
| ORM | Spring Data JPA / Hibernate |
| Mapping | MapStruct |
| Boilerplate | Lombok |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Validation | Jakarta Bean Validation |

## Getting Started

### Prerequisites
- JDK 21+
- Maven 3.9+

### Run the application

```bash
mvn spring-boot:run
```

The API starts on **http://localhost:8080**

### Useful URLs

| URL | Description |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/api-docs | OpenAPI JSON |
| http://localhost:8080/h2-console | H2 database console |

> H2 console credentials: JDBC URL `jdbc:h2:mem:employeedb`, user `sa`, password *(empty)*

---

## API Endpoints

### Employees  `GET /api/v1/employees`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/employees` | List all employees (paginated) |
| GET | `/api/v1/employees/{id}` | Get employee by ID |
| GET | `/api/v1/employees/search?keyword=` | Full-text search |
| GET | `/api/v1/employees/department/{id}` | Get employees by department |
| GET | `/api/v1/employees/status/{status}` | Filter by status |
| POST | `/api/v1/employees` | Create employee |
| PUT | `/api/v1/employees/{id}` | Update employee |
| PATCH | `/api/v1/employees/{id}/status?status=` | Update status only |
| DELETE | `/api/v1/employees/{id}` | Delete employee |

### Departments  `/api/v1/departments`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/departments` | List all departments |
| GET | `/api/v1/departments/{id}` | Get department by ID |
| POST | `/api/v1/departments` | Create department |
| PUT | `/api/v1/departments/{id}` | Update department |
| DELETE | `/api/v1/departments/{id}` | Delete department |

---

## Sample Requests

### Create an employee
```json
POST /api/v1/employees
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane.doe@example.com",
  "phone": "555-9999",
  "jobTitle": "Software Engineer",
  "salary": 90000.00,
  "hireDate": "2024-01-15",
  "departmentId": 1
}
```

### Create a department
```json
POST /api/v1/departments
{
  "name": "DevOps",
  "description": "Platform and Infrastructure team"
}
```

---

## Employee Status Values
- `ACTIVE`
- `INACTIVE`
- `ON_LEAVE`
- `TERMINATED`

## Running Tests

```bash
mvn test
```

## Build JAR

```bash
mvn clean package
java -jar target/employee-management-1.0.0.jar
```
