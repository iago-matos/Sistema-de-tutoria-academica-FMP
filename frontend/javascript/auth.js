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

async function resolveProfileIds(user) {
    if (!user) {
        return { estudanteId: null, tutorId: null };
    }

    let estudanteId = localStorage.getItem("estudanteId");
    let tutorId = localStorage.getItem("tutorId");

    // Sempre re-resolve para garantir consistência após login/troca de sessão
    if (user.tipo === "ESTUDANTE" || user.tipo === "ADMIN") {
        try {
            const estudantes = await fetchEstudantes();
            const match = estudantes.find(function (item) {
                // cast explícito para evitar comparação Number vs String
                return item.usuario && String(item.usuario.id) === String(user.id);
            });
            if (match) {
                estudanteId = String(match.id);
                localStorage.setItem("estudanteId", estudanteId);
            }
        } catch (e) {
            // não bloqueia se a chamada falhar
        }
    }

    if (user.tipo === "TUTOR" || user.tipo === "ADMIN") {
        try {
            const tutores = await fetchTutores();
            const match = tutores.find(function (item) {
                return item.usuario && String(item.usuario.id) === String(user.id);
            });
            if (match) {
                tutorId = String(match.id);
                localStorage.setItem("tutorId", tutorId);
            }
        } catch (e) {
            // não bloqueia se a chamada falhar
        }
    }

    return {
        estudanteId: estudanteId || null,
        tutorId: tutorId || null
    };
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

async function handleSignupSubmit(event) {
    event.preventDefault();

    const payload = {
        nome: document.getElementById("nome").value.trim(),
        email: document.getElementById("email").value.trim(),
        senha: document.getElementById("senha").value
    };
    const feedback = document.getElementById("feedback");

    try {
        await signupRequest(payload);

        if (feedback) {
            feedback.textContent = "Cadastro realizado! Redirecionando...";
            feedback.className = "loading-state";
        }

        setTimeout(function () {
            window.location.href = resolveRelativePath("login.html");
        }, 800);
    } catch (error) {
        if (feedback) {
            feedback.textContent = error.message || "Erro ao cadastrar";
            feedback.className = "error-state";
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
