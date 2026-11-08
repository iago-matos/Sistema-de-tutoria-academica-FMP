const API_BASE = "http://localhost:8080";

function getToken() {
    return localStorage.getItem("Authorization");
}

function authHeaders(extra) {
    const headers = Object.assign({ "Content-Type": "application/json" }, extra || {});
    const token = getToken();

    if (token) {
        headers.Authorization = token;
    }

    return headers;
}

function handleUnauthorized(response) {
    if (response.status === 401) {
        localStorage.removeItem("Authorization");
        localStorage.removeItem("currentUser");
        localStorage.removeItem("estudanteId");
        localStorage.removeItem("tutorId");

        const loginPath = window.location.pathname.includes("/estudante/")
            || window.location.pathname.includes("/tutor/")
            || window.location.pathname.includes("/admin/")
            ? "../login.html"
            : "login.html";

        window.location.href = loginPath;
        return true;
    }

    return false;
}

// Le a mensagem de erro do backend. Em erros de validacao (422), junta as
// mensagens de cada campo, que sao mais uteis que a mensagem geral.
async function readErrorMessage(response) {
    try {
        const body = await response.json();
        if (Array.isArray(body.errors) && body.errors.length) {
            return body.errors.map(function (e) { return e.message; }).join(" ");
        }
        return body.message || body.error || "";
    } catch (_) {
        return "";
    }
}

async function requestJSON(route, options) {
    const config = Object.assign({
        method: "GET",
        headers: authHeaders()
    }, options || {});

    let response;
    try {
        response = await fetch(API_BASE + route, config);
    } catch (networkErr) {
        throw new Error("Sem conexão com o servidor. Verifique se o backend está rodando.");
    }

    if (handleUnauthorized(response)) {
        throw new Error("Sessao expirada");
    }

    if (!response.ok) {
        const detail = await readErrorMessage(response);
        const message = detail
            ? detail
            : "Falha ao carregar " + route + " (" + response.status + ")";
        throw new Error(message);
    }

    if (response.status === 204) {
        return null;
    }

    const contentType = response.headers.get("content-type") || "";

    if (contentType.includes("application/json")) {
        return response.json();
    }

    return null;
}

async function postJSON(route, body) {
    return requestJSON(route, {
        method: "POST",
        body: JSON.stringify(body)
    });
}

async function putJSON(route, body) {
    return requestJSON(route, {
        method: "PUT",
        body: JSON.stringify(body)
    });
}

async function deleteJSON(route) {
    return requestJSON(route, { method: "DELETE" });
}

async function loginRequest(email, senha) {
    const response = await fetch(API_BASE + "/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email, senha: senha })
    });

    // 403: senha correta, mas cadastro pendente ou recusado. O backend explica o motivo.
    if (response.status === 403) {
        throw new Error(await readErrorMessage(response) || "Acesso nao liberado");
    }

    if (!response.ok) {
        throw new Error("Email ou senha invalidos");
    }

    const token = response.headers.get("Authorization");

    if (!token) {
        throw new Error("Token nao recebido");
    }

    localStorage.setItem("Authorization", token);
    return token;
}

// Opcoes da tela de login: client ID do Google, dominio institucional e se o
// cadastro com senha esta ligado. Rota publica.
async function fetchLoginConfig() {
    const response = await fetch(API_BASE + "/login/config");
    if (!response.ok) {
        throw new Error("Nao foi possivel carregar as opcoes de login");
    }
    return response.json();
}

// Envia o ID token do Google (e, no primeiro acesso, o perfil escolhido).
// Devolve o corpo da resposta; em AUTENTICADO o token ja fica salvo.
async function loginGoogleRequest(payload) {
    let response;
    try {
        response = await fetch(API_BASE + "/login/google", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });
    } catch (networkErr) {
        throw new Error("Sem conexão com o servidor. Verifique se o backend está rodando.");
    }

    if (!response.ok) {
        throw new Error(await readErrorMessage(response) || "Nao foi possivel entrar com o Google");
    }

    const body = await response.json();

    if (body.situacao === "AUTENTICADO") {
        const token = response.headers.get("Authorization");
        if (!token) {
            throw new Error("Token nao recebido");
        }
        localStorage.setItem("Authorization", token);
    }

    return body;
}

async function signupRequest(payload) {
    const response = await fetch(API_BASE + "/usuario", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    if (!response.ok) {
        throw new Error(await readErrorMessage(response) || "Erro ao cadastrar");
    }

    return response.json();
}

async function fetchCadastrosPendentes() {
    return requestJSON("/usuario/pendentes");
}

async function aprovarCadastro(usuarioId) {
    return putJSON("/usuario/" + usuarioId + "/aprovar", {});
}

async function recusarCadastro(usuarioId) {
    return putJSON("/usuario/" + usuarioId + "/recusar", {});
}

async function fetchCurrentUser() {
    return requestJSON("/usuario/me");
}

async function fetchEstudantes() {
    return requestJSON("/estudante");
}

async function fetchTutores() {
    return requestJSON("/tutor");
}

async function fetchUsuarios() {
    return requestJSON("/usuario");
}

async function fetchAgendamentos() {
    return requestJSON("/agendamento");
}

async function fetchAvaliacoes() {
    return requestJSON("/avaliacao");
}

async function fetchCoordenadores() {
    return requestJSON("/coordenador");
}

async function fetchEstudanteAgendamentos(estudanteId) {
    return requestJSON("/estudante/" + estudanteId + "/agendamentos");
}

async function fetchEstudanteAvaliacoes(estudanteId) {
    return requestJSON("/estudante/" + estudanteId + "/avaliacoes");
}

async function fetchTutorAgendamentos(tutorId) {
    return requestJSON("/tutor/" + tutorId + "/agendamentos");
}

async function fetchTutorAvaliacoes(tutorId) {
    return requestJSON("/tutor/" + tutorId + "/avaliacoes");
}

async function fetchSessoesDisponiveis() {
    return requestJSON("/agendamento/disponiveis");
}

async function fetchSessoesDisponiveisPorTutor(tutorId) {
    return requestJSON("/agendamento/disponiveis/tutor/" + tutorId);
}

async function createAgendamento(payload) {
    return postJSON("/agendamento", payload);
}

// O backend identifica o estudante pelo token; nao e preciso enviar o ID.
// modalidade: "PRESENCIAL" (padrao) ou "ONLINE" (se o tutor deixou link)
// disciplinaId: a disciplina da tutoria (obrigatoria se o tutor atende mais de uma)
async function solicitarAgendamento(agendamentoId, modalidade, disciplinaId) {
    return putJSON("/agendamento/" + agendamentoId + "/solicitar", {
        modalidade: modalidade || "PRESENCIAL",
        disciplinaId: disciplinaId || null
    });
}

// ── Disciplinas ──────────────────────────────────────────────────────────

// Lista mantida pela coordenacao. Rota publica: usada no primeiro acesso, antes do login
async function fetchDisciplinas() {
    let response;
    try {
        response = await fetch(API_BASE + "/disciplina");
    } catch (networkErr) {
        throw new Error("Sem conexão com o servidor. Verifique se o backend está rodando.");
    }
    if (!response.ok) {
        throw new Error("Não foi possível carregar as disciplinas");
    }
    return response.json();
}

// Coordenacao (admin)
async function criarDisciplina(nome) {
    return postJSON("/disciplina", { nome: nome });
}

async function renomearDisciplina(id, nome) {
    return putJSON("/disciplina/" + id, { nome: nome });
}

async function excluirDisciplina(id) {
    return deleteJSON("/disciplina/" + id);
}

async function fetchPedidosDisciplina() {
    return requestJSON("/disciplina/pedidos");
}

async function aprovarPedidoDisciplina(id) {
    return putJSON("/disciplina/pedidos/" + id + "/aprovar", {});
}

async function recusarPedidoDisciplina(id) {
    return putJSON("/disciplina/pedidos/" + id + "/recusar", {});
}

// Tutor logado: guia "Disciplinas"
async function fetchMinhasDisciplinas() {
    return requestJSON("/tutor/me/disciplinas");
}

async function pedirDisciplina(disciplinaId) {
    return postJSON("/tutor/me/disciplinas", { disciplinaId: disciplinaId });
}

async function removerMinhaDisciplina(vinculoId) {
    return deleteJSON("/tutor/me/disciplinas/" + vinculoId);
}

// Link da videoconferencia: o tutor sempre ve; o estudante, depois da confirmacao
async function fetchLinkReuniao(agendamentoId) {
    return requestJSON("/agendamento/" + agendamentoId + "/link-reuniao");
}

async function updateAgendamento(id, payload) {
    return putJSON("/agendamento/" + id, payload);
}

async function createAvaliacao(payload) {
    return postJSON("/avaliacao", payload);
}


// ── Agendamento: metodos da classe Agendamento no backend ─────────────────

// visualizar(): agenda de quem esta logado (tutor ou estudante)
async function fetchMinhaAgenda() {
    return requestJSON("/agendamento/minha-agenda");
}

// visualizar(Disciplina): horarios livres de uma disciplina
async function fetchHorariosPorDisciplina(disciplina) {
    return requestJSON("/agendamento/disponiveis?disciplina=" + encodeURIComponent(disciplina));
}

// Tutor: cancela a sessao. Estudante: desiste e o horario volta a ficar livre.
async function cancelarAgendamento(id) {
    return putJSON("/agendamento/" + id + "/cancelar", {});
}

// Coordenacao: inativa a conta. Nada e apagado (sessoes, avaliacoes e mensagens ficam)
async function inativarUsuario(usuarioId) {
    return putJSON("/usuario/" + usuarioId + "/inativar", {});
}

async function reativarUsuario(usuarioId) {
    return putJSON("/usuario/" + usuarioId + "/reativar", {});
}

// Apaga nome e e-mail (LGPD) a pedido do proprio usuario, feito na secretaria. Irreversivel.
async function anonimizarUsuario(usuarioId) {
    return putJSON("/usuario/" + usuarioId + "/anonimizar", {});
}
