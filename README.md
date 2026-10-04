# Sistema de Tutoria Academica (TutoriaFMP)

API REST em Spring Boot para agendamento de sessoes de tutoria entre estudantes e
tutores, com frontend estatico em HTML/CSS/JavaScript.

## Estrutura

```
TutoriaFMP-main/
├── .vscode/              Configuracao do VS Code (JDK, launch, task Maven)
├── backend/              Projeto Maven — API REST
│   ├── pom.xml
│   └── src/main/
│       ├── java/br/com/tutoria/
│       │   ├── TutoriaApplication.java    Classe main
│       │   ├── configs/                   SecurityConfig, WebConfig
│       │   ├── Security/                  Filtros JWT, UserDetails
│       │   ├── controller/                Endpoints REST
│       │   ├── service/                   Regras de negocio
│       │   ├── repository/                Spring Data JPA
│       │   ├── model/                     Entidades JPA + enums
│       │   ├── dto/                       UsuarioMeDTO
│       │   └── exceptions/                Tratamento global de erros
│       └── resources/application.properties
└── frontend/             Paginas estaticas (nao servidas pelo backend)
    ├── index.html  login.html  signup.html
    ├── css/  javascript/
    └── admin/  estudante/  tutor/
```

## Tecnologias

- Java 21, Spring Boot 3.5.14
- Spring Web, Spring Data JPA (Hibernate), Spring Security, Spring Validation
- MySQL 8 (`mysql-connector-j`)
- JJWT 0.12.6 (autenticacao via JSON Web Token)
- Lombok
- Frontend: HTML/CSS/JavaScript puro, sem framework e sem build

## Pre-requisitos

- JDK 21 ou superior
- Maven
- MySQL rodando em `localhost:3306`

## Banco de dados

O schema precisa existir antes de subir a aplicacao. As tabelas sao criadas
automaticamente pelo Hibernate (`spring.jpa.hibernate.ddl-auto=update`).

```sql
CREATE DATABASE tutoria_db;
```

Credenciais e URL ficam em `backend/src/main/resources/application.properties`.

## Como executar

### Backend

```bash
cd backend
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`.

No VS Code, a configuracao `TutoriaApplication (Spring Boot)` (F5) faz o mesmo.

### Frontend

O backend **nao** serve as paginas estaticas: `spring.web.resources.add-mappings`
esta como `false` e a pasta `frontend/` fica fora de `src/main/resources`.
Abra `frontend/` com um servidor HTTP local (por exemplo, a extensao Live Server
do VS Code). O CORS do backend aceita qualquer porta em `localhost` e `127.0.0.1`.

O endereco da API esta fixo em `frontend/javascript/api.js`:

```javascript
const API_BASE = "http://localhost:8080";
```

## Autenticacao

- `POST /usuario` — cadastro (rota publica)
- `POST /login` — recebe `{ "email": "...", "senha": "..." }` e devolve o token
  JWT no header `Authorization` da resposta
- As demais rotas exigem o header `Authorization: Bearer <token>`
