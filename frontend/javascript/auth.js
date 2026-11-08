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

function mostrarFeedback(texto, classe) {
    const feedback = document.getElementById("feedback");
    if (!feedback) {
        alert(texto);
        return;
    }
    feedback.textContent = texto;
    feedback.className = classe;
    feedback.style.display = "block";
}

function esconderFeedback() {
    const feedback = document.getElementById("feedback");
    if (feedback) feedback.style.display = "none";
}

// Depois que o token foi salvo: carrega o usuario e vai para o painel dele
async function concluirLogin() {
    const user = await loadCurrentUser(true);
    await resolveProfileIds(user);
    redirectByTipo(user.tipo);
}

async function handleLoginSubmit(event) {
    event.preventDefault();

    const email = document.getElementById("email").value.trim();
    const senha = document.getElementById("senha").value;

    try {
        await loginRequest(email, senha);
        await concluirLogin();
    } catch (error) {
        mostrarFeedback(error.message || "Nao foi possivel entrar", "error-state");
    }
}

// ── Login com Google (conta institucional) ────────────────────────────────

const GOOGLE_SCRIPT_URL = "https://accounts.google.com/gsi/client";

// ID token do Google guardado entre o clique no botao e o envio do formulario
// de primeiro acesso. Fica so em memoria, nunca no localStorage.
let googleCredential = null;

function carregarScriptGoogle() {
    return new Promise(function (resolve, reject) {
        if (window.google && window.google.accounts) {
            resolve();
            return;
        }
        const script = document.createElement("script");
        script.src = GOOGLE_SCRIPT_URL;
        script.async = true;
        script.onload = resolve;
        script.onerror = function () { reject(new Error("Nao foi possivel carregar o login do Google")); };
        document.head.appendChild(script);
    });
}

// Rodape da tela de login: como criar conta depende da configuracao do backend
function renderRodapeLogin(config) {
    const rodape = document.getElementById("authFooter");
    if (!rodape) return;

    rodape.textContent = "";

    if (config.cadastroComSenha) {
        rodape.append("Não possui cadastro? ");
        const link = document.createElement("a");
        link.href = "signup.html";
        link.textContent = "Cadastrar-se";
        rodape.append(link);
    } else if (config.googleClientId) {
        const dominios = config.dominiosInstitucionais.map(function (d) { return "@" + d; }).join(" ou ");
        rodape.textContent = "Primeiro acesso? Entre com sua conta Google " + dominios + ".";
    } else {
        rodape.textContent = "Primeiro acesso? Procure a coordenação.";
    }
}

async function initLoginPage() {
    bindTipoCadastro();

    let config;
    try {
        config = await fetchLoginConfig();
    } catch (error) {
        // Backend fora do ar: fica so o formulario; o erro aparece ao tentar entrar
        return;
    }

    renderRodapeLogin(config);

    if (!config.googleClientId) return;

    try {
        await carregarScriptGoogle();
    } catch (error) {
        mostrarFeedback(error.message, "error-state");
        return;
    }

    const opcoesGoogle = {
        client_id: config.googleClientId,
        callback: handleGoogleCredential,
        ux_mode: "popup",
        auto_select: false
    };
    // Com um unico dominio, a tela de contas do Google sugere so as da instituicao.
    // Com varios, o filtro esconderia contas validas. O backend confere de qualquer forma.
    if (config.dominiosInstitucionais.length === 1) {
        opcoesGoogle.hd = config.dominiosInstitucionais[0];
    }
    google.accounts.id.initialize(opcoesGoogle);

    const area = document.getElementById("googleLogin");
    area.style.display = "";

    const alvo = document.getElementById("googleButton");
    google.accounts.id.renderButton(alvo, {
        type: "standard",
        theme: "outline",
        size: "large",
        text: "signin_with",
        shape: "rectangular",
        logo_alignment: "center",
        locale: "pt-BR",
        width: Math.min(400, alvo.offsetWidth || 336)
    });
}

function handleGoogleCredential(response) {
    googleCredential = response.credential;
    enviarLoginGoogle({ credential: googleCredential });
}

async function enviarLoginGoogle(payload) {
    esconderFeedback();

    try {
        const resultado = await loginGoogleRequest(payload);

        if (resultado.situacao === "AUTENTICADO") {
            googleCredential = null;
            await concluirLogin();
            return;
        }

        if (resultado.situacao === "CADASTRO_NECESSARIO") {
            mostrarCadastroGoogle(resultado);
            return;
        }

        // AGUARDANDO_APROVACAO: cadastro de tutor enviado
        cancelarCadastroGoogle();
        mostrarFeedback(resultado.message, "loading-state");
    } catch (error) {
        mostrarFeedback(error.message || "Nao foi possivel entrar com o Google", "error-state");
    }
}

function mostrarCadastroGoogle(resultado) {
    document.getElementById("loginArea").style.display = "none";
    document.getElementById("googleCadastro").style.display = "";
    document.getElementById("googleCadastroNome").textContent = resultado.nome;
    document.getElementById("googleCadastroEmail").textContent = resultado.email;
}

function cancelarCadastroGoogle() {
    googleCredential = null;
    document.getElementById("googleCadastro").style.display = "none";
    document.getElementById("loginArea").style.display = "";
}

async function handleGoogleCadastroSubmit(event) {
    event.preventDefault();

    if (!googleCredential) {
        cancelarCadastroGoogle();
        mostrarFeedback("Clique em \"Fazer login com o Google\" novamente.", "error-state");
        return;
    }

    const tipo = document.getElementById("tipo").value;
    await enviarLoginGoogle({
        credential: googleCredential,
        tipo: tipo,
        disciplinaId: tipo === "TUTOR" ? Number(document.getElementById("disciplina").value) : null
    });
}

// Tela de cadastro com senha: some quando o backend desligou essa opcao
async function initSignupPage() {
    bindTipoCadastro();

    try {
        const config = await fetchLoginConfig();
        if (!config.cadastroComSenha) {
            document.getElementById("signupForm").style.display = "none";
            document.getElementById("signupDesativado").style.display = "";
        }
    } catch (error) {
        // Backend fora do ar: mantem o formulario; o erro aparece ao enviar
    }
}

// Preenche o seletor de disciplina do cadastro com a lista da coordenacao
async function carregarDisciplinasDoCadastro() {
    const select = document.getElementById("disciplina");
    if (!select) return;
    try {
        const disciplinas = await fetchDisciplinas();
        select.innerHTML = "<option value=\"\">Selecione</option>" + disciplinas.map(function (d) {
            return "<option value=\"" + escapeHtmlAuth(d.id) + "\">" + escapeHtmlAuth(d.nome) + "</option>";
        }).join("");
    } catch (error) {
        select.innerHTML = "<option value=\"\">Não foi possível carregar as disciplinas</option>";
    }
}

// utils.js nao e carregado nas telas de login e cadastro
function escapeHtmlAuth(valor) {
    return String(valor == null ? "" : valor)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
}

// O tutor escolhe a disciplina; o estudante so escolhe o tipo
// (os dias das sessoes vem da agenda do tutor)
function bindTipoCadastro() {
    const tipo = document.getElementById("tipo");
    if (!tipo) return;
    carregarDisciplinasDoCadastro();

    function atualizarCampos() {
        const tutor = tipo.value === "TUTOR";
        document.getElementById("campoDisciplina").style.display = tutor ? "" : "none";
        document.getElementById("disciplina").required = tutor;

        const aviso = document.getElementById("avisoTipo");
        if (aviso) {
            aviso.textContent = tutor
                ? "Seu acesso será liberado depois que a coordenação aprovar o cadastro. Depois, você abre os seus horários na agenda."
                : "Você entra na hora e escolhe os horários disponíveis na agenda dos tutores.";
        }
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
        disciplinaId: tipo === "TUTOR" ? Number(document.getElementById("disciplina").value) : null
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
