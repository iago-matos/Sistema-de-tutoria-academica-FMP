# Sistema de Tutoria Academica (TutoriaFMP)

API REST em Spring Boot para agendamento de sessoes de tutoria entre estudantes e
tutores, com frontend estatico em HTML/CSS/JavaScript.

## Estrutura

```
TutoriaFMP-main/
├── .github/workflows/    CI: build e testes a cada push e Pull Request
├── .vscode/              Configuracao do VS Code (JDK, launch, task Maven)
├── docker-compose.yml    SQL Server para desenvolvimento local (opcional)
├── backend/              Projeto Maven — API REST
│   ├── pom.xml
│   ├── .env.example      Modelo das variaveis de ambiente
│   ├── src/test/         Testes automatizados (banco H2 em memoria)
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
- SQL Server / Azure SQL Database (camada serverless, oferta gratuita)
- JJWT 0.12.6 (autenticacao via JSON Web Token)
- Lombok
- Testes: JUnit 5, Spring Boot Test, Spring Security Test, H2
- CI: GitHub Actions
- Frontend: HTML/CSS/JavaScript puro, sem framework e sem build

## Pre-requisitos

- JDK 21 ou superior
- Maven
- Um banco SQL Server acessivel (veja as opcoes abaixo)

## Banco de dados

O banco precisa existir antes de subir a aplicacao. As tabelas sao criadas
automaticamente pelo Hibernate (`spring.jpa.hibernate.ddl-auto=update`).

### Opcao 1 (recomendada): banco de desenvolvimento na Azure

Nao exige instalar nada. A oferta gratuita do Azure SQL permite mais de um banco
gratuito por assinatura (ate 10, segundo a documentacao da Microsoft).

1. No servidor do projeto na Azure, crie um segundo banco, `tutoria_db_dev`,
   tambem com a oferta gratuita
2. Em *Servidor → Seguranca → Rede*, clique em **Adicionar o endereco IPv4 do
   cliente** para liberar o seu computador (repita se o seu IP mudar)
3. No `backend/.env`, use a `DB_URL` do Azure SQL do `.env.example`, trocando
   `tutoria_db` por `tutoria_db_dev`

O banco pausa quando ninguem usa. A primeira conexao depois de uma pausa leva
cerca de 1 minuto: a aplicacao espera, nao e erro.

### Opcao 2: SQL Server instalado no computador

1. Instale o **SQL Server 2022 Express** ou **Developer** (gratuitos) com
   instalacao personalizada
2. Na configuracao do mecanismo de banco de dados, escolha **Modo misto** e
   defina a senha do usuario `sa`
3. No **SQL Server Configuration Manager**, em *Configuracao de Rede*, habilite o
   protocolo **TCP/IP**. Nas propriedades dele, em *IPAll*, apague o valor de
   *Portas Dinamicas TCP*, coloque **1433** em *Porta TCP* e reinicie o servico
4. Crie o banco (pelo SQL Server Management Studio ou pelo `sqlcmd`):

```sql
CREATE DATABASE tutoria_db;
```

### Opcao 3: Docker

`docker compose up -d` na raiz do projeto e, depois, o comando de criacao do
banco que esta comentado no `docker-compose.yml`.

## Configuracao

Nenhuma senha ou segredo fica no codigo. Tudo vem de variaveis de ambiente,
que em desenvolvimento ficam no arquivo `backend/.env` (ignorado pelo Git).

1. Copie o modelo:

   ```bash
   cd backend
   cp .env.example .env
   ```

2. Edite o `backend/.env`:
   - `DB_URL`, `DB_USERNAME` e `DB_PASSWORD`: os dados do banco escolhido acima
   - `JWT_SECRET`: um segredo aleatorio de no minimo 32 caracteres. No Git Bash:

     ```bash
     openssl rand -base64 48
     ```

   - `ADMIN_EMAILS`: e-mails dos administradores, separados por virgula
   - `ADMIN_SENHA_INICIAL`: senha dos administradores criados pela primeira vez
     (minimo de 8 caracteres)

Cada pessoa da equipe tem o seu proprio `.env`. Se uma variavel obrigatoria
faltar, a aplicacao nao sobe e a mensagem de erro indica qual.

## Como executar

### Backend

```bash
cd backend
mvn spring-boot:run
```

### Testes

```bash
cd backend
mvn verify
```

Os testes usam um banco H2 em memoria (modo SQL Server) e nao precisam de banco
instalado nem de `.env`.
O GitHub Actions roda os mesmos testes a cada push na `main` e em todo Pull Request.

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

## Cadastro e acesso

1. **Administradores:** ao subir, a aplicacao garante que cada e-mail de
   `ADMIN_EMAILS` exista como administrador ativo. Se o e-mail ainda nao existir,
   a conta e criada com a senha `ADMIN_SENHA_INICIAL`.
2. **Estudantes e tutores** se cadastram pela tela de cadastro, escolhendo o tipo
   de perfil. A conta nasce **PENDENTE**.
3. Um administrador aprova ou recusa o cadastro na tela **Aprovacoes**.
4. So contas **ATIVAS** conseguem fazer login.

## Permissoes

A identidade de quem faz a requisicao vem sempre do token JWT, nunca de IDs
enviados no corpo.

| Acao | Estudante | Tutor | Admin |
|---|---|---|---|
| Ver sessoes disponiveis e lista de tutores (sem e-mail) | sim | sim | sim |
| Solicitar sessao | em nome proprio | nao | nao |
| Criar sessao disponivel | nao | na propria agenda | para qualquer tutor |
| Confirmar, recusar ou alterar sessao | nao | so as proprias | sim |
| Excluir agendamento | nao | nao | sim |
| Registrar avaliacao | em nome proprio | nao | nao |
| Editar ou excluir avaliacao | nao | nao | sim |
| Ver os proprios agendamentos e avaliacoes | sim | sim | todos |
| Listar estudantes, avaliacoes e agendamentos de todos | nao | nao | sim |
| Coordenadores, aprovacao de cadastros, usuarios | nao | nao | sim |

Cadastrar um coordenador **nao** concede o perfil de administrador.

## Autenticacao

- `POST /usuario` — cadastro (rota publica). Corpo:
  `{ "nome", "email", "senha", "tipo": "ESTUDANTE" | "TUTOR", "diaDaSemana" | "disciplina" }`
- `POST /login` — recebe `{ "email": "...", "senha": "..." }` e devolve o token
  JWT no header `Authorization` da resposta. Conta pendente ou recusada recebe
  **403** com o motivo; credenciais erradas recebem **401**.
- As demais rotas exigem o header `Authorization: Bearer <token>`
- Rotas de administracao: `GET /usuario/pendentes`, `PUT /usuario/{id}/aprovar`,
  `PUT /usuario/{id}/recusar`
