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
   - `GOOGLE_CLIENT_ID`: ID do cliente OAuth do login com Google (veja abaixo).
     Vazio: o botao do Google nao aparece
   - `GOOGLE_DOMINIOS`: dominios aceitos no login com Google, separados por
     virgula (padrao `fmpsc.edu.br,aluno.fmpsc.edu.br`)
   - `CADASTRO_COM_SENHA`: `true` reativa o cadastro com e-mail e senha. Use so em
     desenvolvimento; o padrao e `false`

### Login com Google

Os e-mails da FMP (`@fmpsc.edu.br` e `@aluno.fmpsc.edu.br`) sao contas Google
Workspace, entao o aluno entra com o botao **Fazer login com o Google**. O backend
so aceita contas desses dominios, com e-mail verificado pelo Google. E gratuito.

1. No [Google Cloud Console](https://console.cloud.google.com/), crie um projeto
   (por exemplo, `tutoria-fmp`)
2. Em *APIs e servicos → Tela de consentimento OAuth* (ou *Google Auth Platform*):
   tipo de usuario **Externo**, nome do app, e-mail de suporte. Os escopos
   `openid`, `email` e `profile` sao basicos e nao passam por verificacao do Google.
   Publique o app (*Em producao*); em modo de teste, so os e-mails cadastrados
   como testadores conseguem entrar
3. Em *Credenciais → Criar credenciais → ID do cliente OAuth*, tipo **Aplicativo
   da Web**. Em *Origens JavaScript autorizadas*, adicione o endereco de onde o
   frontend e aberto, sem barra no final:
   - desenvolvimento: `http://localhost` e `http://localhost:5500` (Live Server)
   - producao: o endereco do frontend no GitHub Pages, `https://iago-matos.github.io`

   Nao e preciso URI de redirecionamento nem chave secreta.
4. Copie o *ID do cliente* (termina em `.apps.googleusercontent.com`) para
   `GOOGLE_CLIENT_ID` e reinicie o backend

Abra o frontend por `http://localhost:5500`, e nao por `127.0.0.1`: o Google so
mostra o botao nas origens cadastradas no passo 3.

Se a FMP bloquear apps de terceiros no Google Workspace, o Google mostra
"Acesso bloqueado" ao entrar. Nesse caso, a TI da FMP precisa liberar o ID do
cliente em *Admin Console → Seguranca → Controles de API*.

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

### Frontend publicado (GitHub Pages)

O workflow `.github/workflows/pages.yml` publica a pasta `frontend/` a cada push
na `main` que altere o frontend (ou manualmente, pela aba *Actions*). Endereco:
<https://iago-matos.github.io/Sistema-de-tutoria-academica-FMP/>

Primeira vez: em *Settings → Pages → Source*, escolha **GitHub Actions**.

Enquanto o backend roda localmente, a pagina publicada so funciona no computador
que esta rodando o backend:

- inclua `https://iago-matos.github.io` em `CORS_ALLOWED_ORIGINS`;
- o Chrome pede permissao para o site acessar a rede local; aceite;
- para o login com Google, cadastre a mesma origem no Google Cloud Console.

Quando o backend estiver na Azure, troque o `API_BASE` pelo endereco dele.

## Cadastro e acesso

1. **Administradores:** ao subir, a aplicacao garante que cada e-mail de
   `ADMIN_EMAILS` exista como administrador ativo. Se o e-mail ainda nao existir,
   a conta e criada com a senha `ADMIN_SENHA_INICIAL`.
2. **Estudantes e tutores** entram com a conta Google institucional
   (`@fmpsc.edu.br` ou `@aluno.fmpsc.edu.br`). No primeiro acesso, escolhem o perfil:
   - **Estudante:** a conta ja nasce **ATIVA**, porque o e-mail institucional
     comprova o vinculo com a faculdade;
   - **Tutor:** escolhe uma disciplina da lista e a conta nasce **PENDENTE**.
     Um administrador aprova ou recusa na tela **Aprovacoes** (a disciplina
     escolhida e aprovada junto com a conta).
3. Se ja existir uma conta com o mesmo e-mail (criada com senha), o login com
   Google entra nela.
4. O cadastro com e-mail e senha fica desligado (`CADASTRO_COM_SENHA=false`).
   Contas antigas, como as dos administradores, continuam entrando com senha.
5. So contas **ATIVAS** conseguem fazer login.

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
| Pedir para atender outra disciplina | nao | sim (aguarda aprovacao) | nao |
| Manter a lista de disciplinas e aprovar pedidos de disciplina | nao | nao | sim |

Cadastrar um coordenador **nao** concede o perfil de administrador.

## Disciplinas

- A **coordenacao** (admin) mantem a lista de disciplinas na guia **Disciplinas**.
  A lista inicial vem de `DISCIPLINAS_INICIAIS` (padrao: Matematica, Logica de
  Programacao, Banco de Dados e Algoritmos) e so e criada no primeiro uso.
- O **tutor** pode atender varias disciplinas. A do cadastro ja vale; as pedidas
  depois, na guia **Disciplinas** do tutor, ficam aguardando a aprovacao na guia
  **Aprovacoes**. O tutor precisa manter ao menos uma disciplina ativa.
- O **horario livre** do tutor aparece em todas as disciplinas ativas dele. Ao
  solicitar, o estudante escolhe a disciplina, que fica registrada na tutoria.
- Bancos criados antes desta versao sao migrados ao subir a aplicacao
  (`AjusteDeEsquema`): o texto antigo da disciplina de cada tutor vira uma
  disciplina da lista, e a coluna antiga e apagada.

## Autenticacao

- `GET /login/config` — rota publica com `googleClientId`, `dominioInstitucional`
  e `cadastroComSenha`, usada pela tela de login
- `POST /login/google` — recebe `{ "credential": "<ID token do Google>" }`.
  Respostas pelo campo `situacao`:
  - `AUTENTICADO` (200): token JWT no header `Authorization`;
  - `CADASTRO_NECESSARIO` (200): primeiro acesso. Reenvie com
    `"tipo": "ESTUDANTE" | "TUTOR"` e, para tutor, `"disciplinaId"` (da lista);
  - `AGUARDANDO_APROVACAO` (202): conta de tutor criada, pendente.

  Conta de outro dominio, ou pendente, recusada ou inativa: **403** com o motivo.
  Token invalido ou expirado: **401**.
- `POST /usuario` — cadastro com senha (rota publica; **403** se
  `CADASTRO_COM_SENHA=false`). Corpo:
  `{ "nome", "email", "senha", "tipo": "ESTUDANTE" | "TUTOR", "disciplinaId" }`
  (`disciplina` so para tutor)
- `POST /login` — recebe `{ "email": "...", "senha": "..." }` e devolve o token
  JWT no header `Authorization` da resposta. Conta pendente ou recusada recebe
  **403** com o motivo; credenciais erradas recebem **401**.
- As demais rotas exigem o header `Authorization: Bearer <token>`
- Rotas de administracao: `GET /usuario/pendentes`, `PUT /usuario/{id}/aprovar`,
  `PUT /usuario/{id}/recusar`
- Disciplinas: `GET /disciplina` (publica); `POST`, `PUT /{id}` e `DELETE /{id}`
  (admin); pedidos dos tutores em `GET /disciplina/pedidos` e
  `PUT /disciplina/pedidos/{id}/aprovar|recusar` (admin)
- Disciplinas do tutor logado: `GET` e `POST /tutor/me/disciplinas`
  (`{ "disciplinaId" }`), `DELETE /tutor/me/disciplinas/{id}`
- Solicitar tutoria: `PUT /agendamento/{id}/solicitar` com
  `{ "modalidade", "disciplinaId" }`. A disciplina e obrigatoria quando o tutor
  atende mais de uma.
