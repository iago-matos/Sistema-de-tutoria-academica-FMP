const DASHBOARD_PATHS = {
    ESTUDANTE: "estudante/dashboard.html",
    TUTOR: "tutor/dashboard.html",
    ADMIN: "admin/dashboard.html"
};

function getStoredUser() {
    const raw = localStorage.getItem("currentUser");
    return raw ? JSON.parse(raw) : null;
}

function saveUser(user) {
    localStorage.setItem("currentUser", JSON.stringify(user));
}

function logout() {
    localStorage.removeItem("Authorization");
    localStorage.removeItem("currentUser");
    localStorage.removeItem("estudanteId");
    localStorage.removeItem("tutorId");

    const loginPath = resolveRelativePath("login.html");
    window.location.href = loginPath;
}

function resolveRelativePath(filename) {
    const depth = (window.location.pathname.match(/\//g) || []).length;
    const inModule = window.location.pathname.includes("/estudante/")
        || window.location.pathname.includes("/tutor/")
        || window.location.pathname.includes("/admin/");

    return inModule ? "../" + filename : filename;
}

function redirectByTipo(tipo) {
    const target = DASHBOARD_PATHS[tipo] || DASHBOARD_PATHS.ESTUDANTE;
    window.location.href = resolveRelativePath(target);
}

async function loadCurrentUser(forceRefresh) {
    if (!getToken()) {
        return null;
    }

    if (!forceRefresh) {
        const cached = getStoredUser();
        if (cached) {
            return cached;
        }
    }

    const user = await fetchCurrentUser();
    saveUser(user);
    return user;
}

// O GET /usuario/me ja informa os IDs dos perfis do usuario logado
async function resolveProfileIds(user) {
    if (!user) {
        return { estudanteId: null, tutorId: null };
    }

    // Sessoes salvas antes desta versao nao tem os IDs: busca de novo
    if (!("estudanteId" in user)) {
        user = await loadCurrentUser(true);
    }

    const estudanteId = user.estudanteId != null ? String(user.estudanteId) : null;
    const tutorId = user.tutorId != null ? String(user.tutorId) : null;

    if (estudanteId) localStorage.setItem("estudanteId", estudanteId);
    else localStorage.removeItem("estudanteId");

    if (tutorId) localStorage.setItem("tutorId", tutorId);
    else localStorage.removeItem("tutorId");

    return { estudanteId: estudanteId, tutorId: tutorId };
}

async function requireAuth(expectedTipo) {
    if (!getToken()) {
        window.location.href = resolveRelativePath("login.html");
        return null;
    }

    try {
        const user = await loadCurrentUser(false);

        if (expectedTipo && user.tipo !== expectedTipo) {
            redirectByTipo(user.tipo);
            return null;
        }

        return user;
    } catch (error) {
        logout();
        return null;
    }
}

async function handleLoginSubmit(event) {
    event.preventDefault();

    const email = document.getElementById("email").value.trim();
    const senha = document.getElementById("senha").value;
    const feedback = document.getElementById("feedback");

    try {
        await loginRequest(email, senha);
        const user = await loadCurrentUser(true);
        await resolveProfileIds(user);
        redirectByTipo(user.tipo);
    } catch (error) {
        if (feedback) {
            feedback.textContent = error.message || "Nao foi possivel entrar";
            feedback.className = "error-state";
            feedback.style.display = "block";
        } else {
            alert(error.message || "Nao foi possivel entrar");
        }
    }
}

// Mostra o campo especifico de cada tipo de perfil no formulario de cadastro
function bindTipoCadastro() {
    const tipo = document.getElementById("tipo");
    if (!tipo) return;

    function atualizarCampos() {
        const estudante = tipo.value === "ESTUDANTE";
        document.getElementById("campoDiaDaSemana").style.display = estudante ? "" : "none";
        document.getElementById("campoDisciplina").style.display = estudante ? "none" : "";
        document.getElementById("diaDaSemana").required = estudante;
        document.getElementById("disciplina").required = !estudante;
    }

    tipo.addEventListener("change", atualizarCampos);
    atualizarCampos();
}

async function handleSignupSubmit(event) {
    event.preventDefault();

    const tipo = document.getElementById("tipo").value;
    const payload = {
        nome: document.getElementById("nome").value.trim(),
        email: document.getElementById("email").value.trim(),
        senha: document.getElementById("senha").value,
        tipo: tipo,
        diaDaSemana: tipo === "ESTUDANTE" ? document.getElementById("diaDaSemana").value : null,
        disciplina: tipo === "TUTOR" ? document.getElementById("disciplina").value.trim() : null
    };
    const feedback = document.getElementById("feedback");

    try {
        await signupRequest(payload);

        if (feedback) {
            feedback.textContent = "Cadastro enviado! Você poderá entrar assim que um administrador aprovar seu acesso.";
            feedback.className = "loading-state";
            feedback.style.display = "block";
        }

        setTimeout(function () {
            window.location.href = resolveRelativePath("login.html");
        }, 3500);
    } catch (error) {
        if (feedback) {
            feedback.textContent = error.message || "Erro ao cadastrar";
            feedback.className = "error-state";
            feedback.style.display = "block";
        } else {
            alert(error.message || "Erro ao cadastrar");
        }
    }
}

async function bootstrapIndexRedirect() {
    if (!getToken()) {
        window.location.href = "login.html";
        return;
    }

    try {
        const user = await loadCurrentUser(true);
        await resolveProfileIds(user);
        window.location.href = DASHBOARD_PATHS[user.tipo] || DASHBOARD_PATHS.ESTUDANTE;
    } catch (error) {
        window.location.href = "login.html";
    }
}

function bindLogoutButtons() {
    document.querySelectorAll("[data-action='logout']").forEach(function (button) {
        button.addEventListener("click", logout);
    });
}

function renderUserBadge() {
    const user = getStoredUser();
    const target = document.getElementById("userBadge");

    if (user && target) {
        target.textContent = user.nome + " (" + user.tipo + ")";
    }
}
