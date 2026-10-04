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
        let detail = "";
        try {
            const body = await response.json();
            detail = body.message || body.error || "";
        } catch (_) {}
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

async function signupRequest(payload) {
    const response = await fetch(API_BASE + "/usuario", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    if (!response.ok) {
        throw new Error("Erro ao cadastrar");
    }

    return response.json();
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

async function solicitarAgendamento(agendamentoId, estudanteId) {
    return putJSON("/agendamento/" + agendamentoId + "/solicitar", { estudanteId: estudanteId });
}

async function updateAgendamento(id, payload) {
    return putJSON("/agendamento/" + id, payload);
}

async function createAvaliacao(payload) {
    return postJSON("/avaliacao", payload);
}
