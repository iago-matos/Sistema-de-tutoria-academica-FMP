const PAGE_LOADERS = {
    "estudante-dashboard": loadEstudanteDashboard,
    "estudante-buscar-tutores": loadEstudanteBuscarTutores,
    "estudante-solicitar-agendamento": loadEstudanteSolicitarAgendamento,
    "estudante-meus-agendamentos": loadEstudanteMeusAgendamentos,
    "estudante-avaliacoes": loadEstudanteAvaliacoes,
    "tutor-dashboard": loadTutorDashboard,
    "tutor-solicitacoes": loadTutorSolicitacoes,
    "tutor-agenda": loadTutorAgenda,
    "tutor-avaliacoes": loadTutorAvaliacoes,
    "tutor-criar-agendamento": loadTutorCriarAgendamento,
    "admin-dashboard": loadAdminDashboard,
    "admin-usuarios": loadAdminUsuarios
};

let cachedTutores = [];
let cachedAvaliacoes = [];

// Envolve uma promise de fetch e retorna [] em caso de erro,
// evitando que um fetch falho quebre o Promise.all inteiro
function safeArray(promise) {
    return promise.catch(function () { return []; });
}

async function loadEstudanteDashboard() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    const ids = await resolveProfileIds(user);
    showLoader("Carregando dashboard...");

    const [agendamentos, tutores, avaliacoes] = await Promise.all([
        ids.estudanteId ? safeArray(fetchEstudanteAgendamentos(ids.estudanteId)) : Promise.resolve([]),
        safeArray(fetchTutores()),
        ids.estudanteId ? safeArray(fetchEstudanteAvaliacoes(ids.estudanteId)) : Promise.resolve([])
    ]);

    hideLoader();

    const pendingCount = countByStatus(agendamentos, isPendingStatus);
    initAppShell({ notifCount: pendingCount });

    setText("statAgendamentos", countByStatus(agendamentos, isConfirmedStatus));
    setText("statPendentes", pendingCount);
    setText("statTutores", tutores.length);

    renderEstudanteAgendamentoCards("agendamentosPreview", agendamentos.slice(0, 5));
    renderTutorCards("tutoresPreview", tutores.slice(0, 3), avaliacoes);
    renderReviewCards("avaliacoesPreview", avaliacoes, { limit: 2 });
    bindSolicitarTutoriaButtons();
}

async function loadEstudanteBuscarTutores() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    initAppShell({
        notifCount: 0,
        header: { mode: "search", title: "Tutores disponíveis", subtitle: "Carregando..." }
    });

    showLoader("Carregando tutores...");
    cachedTutores = await fetchTutores();
    cachedAvaliacoes = await fetchAvaliacoes();
    hideLoader();

    updatePageSubtitle(cachedTutores.length + " tutores encontrados");
    renderTutorCards("tutoresLista", cachedTutores, cachedAvaliacoes);
    bindSolicitarTutoriaButtons();

    bindSearchInput(function (query) {
        const filtered = filterTutores(cachedTutores, query, cachedAvaliacoes);
        updatePageSubtitle(filtered.length + " tutores encontrados");
        renderTutorCards("tutoresLista", filtered, cachedAvaliacoes);
        bindSolicitarTutoriaButtons();
    });
}

async function loadEstudanteSolicitarAgendamento() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    const ids = await resolveProfileIds(user);
    const tutorIdParam = getQueryParam("tutorId");

    initAppShell({
        notifCount: 0,
        header: {
            mode: "title",
            title: "Sessões disponíveis",
            subtitle: "Clique em Solicitar para reservar uma sessão com o tutor."
        }
    });

    if (!ids.estudanteId) {
        showError("Perfil de estudante não encontrado. Verifique seu cadastro.");
        return;
    }

    showLoader("Carregando sessões disponíveis...");

    const sessoes = tutorIdParam
        ? await safeArray(fetchSessoesDisponiveisPorTutor(Number(tutorIdParam)))
        : await safeArray(fetchSessoesDisponiveis());

    hideLoader();

    renderSessoesDisponiveis("sessoesLista", sessoes, async function (sessaoId) {
        await solicitarAgendamento(sessaoId, Number(ids.estudanteId));
        showFeedback("feedback", "Solicitação enviada com sucesso!", "success");
    });
}

async function loadEstudanteMeusAgendamentos() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    const ids = await resolveProfileIds(user);

    initAppShell({
        notifCount: 0,
        header: { mode: "title", title: "Minhas solicitações", subtitle: "Acompanhe o status das suas tutorias." }
    });

    showLoader("Carregando agendamentos...");

    const agendamentos = ids.estudanteId
        ? await safeArray(fetchEstudanteAgendamentos(ids.estudanteId))
        : [];

    hideLoader();
    renderEstudanteAgendamentoCards("agendamentos", agendamentos);
}

async function loadEstudanteAvaliacoes() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    const ids = await resolveProfileIds(user);

    initAppShell({
        notifCount: 0,
        header: { mode: "title", title: "Avaliações", subtitle: "Consulte e registre suas avaliações." }
    });

    showLoader("Carregando avaliações...");

    const avaliacoes = ids.estudanteId
        ? await fetchEstudanteAvaliacoes(ids.estudanteId)
        : [];

    hideLoader();
    renderReviewCards("avaliacoesLista", avaliacoes);

    const form = document.getElementById("avaliacaoForm");
    if (!form) return;

    const tutores = await fetchTutores();
    fillSelect("tutorSelect", tutores, function (tutor) {
        return formatUsuario(tutor.usuario) + " — " + tutor.disciplina;
    }, function (tutor) {
        return tutor.id;
    });

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        if (!ids.estudanteId) {
            showFeedback("feedback", "Perfil de estudante não encontrado.", "error");
            return;
        }

        const payload = {
            nota: Number(document.getElementById("nota").value),
            dataAvaliacao: document.getElementById("dataAvaliacao").value,
            horaAvaliacao: document.getElementById("horaAvaliacao").value,
            tutor: { id: Number(document.getElementById("tutorSelect").value) },
            estudante: { id: Number(ids.estudanteId) }
        };

        try {
            await createAvaliacao(payload);
            showFeedback("feedback", "Avaliação registrada com sucesso!", "success");
            form.reset();
            await loadEstudanteAvaliacoes();
        } catch (error) {
            showFeedback("feedback", error.message, "error");
        }
    });
}

async function loadTutorDashboard() {
    const user = await requireAuth("TUTOR");
    if (!user) return;

    const ids = await resolveProfileIds(user);

    if (!ids.tutorId) {
        initAppShell({ notifCount: 0 });
        showError("Perfil de tutor não encontrado. Verifique seu cadastro.");
        return;
    }

    showLoader("Carregando dashboard...");

    const [agendamentos, avaliacoes] = await Promise.all([
        safeArray(fetchTutorAgendamentos(ids.tutorId)),
        safeArray(fetchTutorAvaliacoes(ids.tutorId))
    ]);

    const pendingCount = countByStatus(agendamentos, isPendingStatus);

    hideLoader();
    initAppShell({ notifCount: pendingCount || 0 });

    setText("statAgendamentos", countByStatus(agendamentos, isConfirmedStatus));
    setText("statPendentes", pendingCount);
    setText("statAvaliacao", computeAverageRating(avaliacoes));

    renderPendingRequests("pendingRequests", agendamentos, { limit: 5 });
    renderReviewCards("avaliacoesPreview", avaliacoes, { limit: 2 });
    bindAgendamentoActions(loadTutorDashboard);
}

async function loadTutorSolicitacoes() {
    const user = await requireAuth("TUTOR");
    if (!user) return;

    // CRÍTICO: aguardar resolveProfileIds antes de qualquer fetch
    const ids = await resolveProfileIds(user);

    if (!ids.tutorId) {
        initAppShell({ notifCount: 0, header: { mode: "title", title: "Solicitações pendentes", subtitle: "" } });
        showError("Perfil de tutor não encontrado.");
        return;
    }

    showLoader("Carregando solicitações...");
    const agendamentos = await safeArray(fetchTutorAgendamentos(ids.tutorId));
    const pendingCount = countByStatus(agendamentos, isPendingStatus);
    hideLoader();

    initAppShell({
        notifCount: pendingCount || 0,
        header: { mode: "title", title: "Solicitações pendentes", subtitle: "Confirme ou recuse pedidos de tutoria." }
    });

    renderPendingRequests("pendingRequests", agendamentos);
    bindAgendamentoActions(loadTutorSolicitacoes);
}

async function loadTutorAgenda() {
    const user = await requireAuth("TUTOR");
    if (!user) return;

    // CRÍTICO: aguardar resolveProfileIds antes de qualquer fetch
    const ids = await resolveProfileIds(user);

    initAppShell({
        header: { mode: "title", title: "Agenda", subtitle: "Clique em um horário vazio para criar uma sessão." }
    });

    if (!ids.tutorId) {
        showError("Perfil de tutor não encontrado.");
        return;
    }

    showLoader("Carregando agenda...");
    const agendamentos = await safeArray(fetchTutorAgendamentos(ids.tutorId));
    hideLoader();

    if (document.getElementById("calHeader")) {
        window.tutorId = ids.tutorId;
        if (typeof tutorId !== "undefined") tutorId = ids.tutorId;
        window.agendamentos = agendamentos;
        if (typeof currentWeekStart !== "undefined") buildHeader(currentWeekStart);
        if (typeof currentWeekStart !== "undefined") buildBody(currentWeekStart, agendamentos);
    } else {
        renderAgendamentosTable("agendamentos", agendamentos);
    }
}

async function loadTutorAvaliacoes() {
    const user = await requireAuth("TUTOR");
    if (!user) return;

    const ids = await resolveProfileIds(user);

    initAppShell({
        header: { mode: "title", title: "Avaliações recebidas", subtitle: "Feedback dos seus estudantes." }
    });

    if (!ids.tutorId) {
        showError("Perfil de tutor não encontrado.");
        return;
    }

    showLoader("Carregando avaliações...");
    const avaliacoes = await fetchTutorAvaliacoes(ids.tutorId);
    hideLoader();
    renderReviewCards("avaliacoesLista", avaliacoes);
}

async function loadAdminDashboard() {
    const user = await requireAuth("ADMIN");
    if (!user) return;

    initAppShell();

    showLoader("Carregando dashboard...");

    const results = await Promise.all([
        fetchUsuarios(),
        fetchEstudantes(),
        fetchTutores(),
        fetchAgendamentos(),
        fetchAvaliacoes(),
        fetchCoordenadores()
    ]);

    hideLoader();
    setText("statUsuarios", results[0].length);
    setText("statEstudantes", results[1].length);
    setText("statTutores", results[2].length);
    setText("statAgendamentos", results[3].length);
    setText("statAvaliacoes", results[4].length);
    setText("statCoordenadores", results[5].length);
    renderAgendamentosTable("agendamentos", results[3].slice(0, 8));
    renderUsuariosList("usuariosLista", results[0].slice(0, 5));
}

async function loadAdminUsuarios() {
    const user = await requireAuth("ADMIN");
    if (!user) return;

    initAppShell({
        header: { mode: "title", title: "Usuários", subtitle: "Gerenciamento via GET /usuario." }
    });

    showLoader("Carregando usuários...");
    const usuarios = await fetchUsuarios();
    hideLoader();

    const table = document.getElementById("usuariosTable");
    if (!table) return;

    if (!usuarios.length) {
        table.innerHTML = "<tbody><tr><td colspan=\"5\"><div class=\"empty-state\">Nenhum usuário encontrado.</div></td></tr></tbody>";
        return;
    }

    const rows = usuarios.map(function (usuario) {
        return "<tr>"
            + "<td>#" + escapeHTML(usuario.id) + "</td>"
            + "<td>" + escapeHTML(usuario.nome) + "</td>"
            + "<td>" + escapeHTML(usuario.email) + "</td>"
            + "<td><span class=\"badge\">" + escapeHTML((usuario.profiles || []).join(", ") || "USER") + "</span></td>"
            + "<td><div class=\"admin-actions\">"
            + "<button class=\"btn secondary sm\" type=\"button\" data-action=\"edit-usuario\" data-usuario-id=\"" + escapeHTML(usuario.id) + "\">Editar</button>"
            + "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"delete-usuario\" data-usuario-id=\"" + escapeHTML(usuario.id) + "\">Excluir</button>"
            + "</div></td></tr>";
    }).join("");

    table.innerHTML = "<thead><tr><th>ID</th><th>Nome</th><th>Email</th><th>Perfis</th><th>Ações</th></tr></thead><tbody>" + rows + "</tbody>";

    document.querySelectorAll("[data-action='delete-usuario']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const id = button.getAttribute("data-usuario-id");
            if (!confirm("Deseja excluir o usuário #" + id + "?")) return;

            try {
                await deleteJSON("/usuario/" + id);
                await loadAdminUsuarios();
            } catch (error) {
                alert(error.message);
            }
        });
    });

    document.querySelectorAll("[data-action='edit-usuario']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const id = button.getAttribute("data-usuario-id");
            const usuario = usuarios.find(function (u) { return String(u.id) === String(id); });
            if (!usuario) return;

            const novoNome = prompt("Novo nome:", usuario.nome);
            if (!novoNome) return;

            try {
                await putJSON("/usuario/" + id, {
                    nome: novoNome,
                    email: usuario.email,
                    senha: usuario.senha || "placeholder"
                });
                await loadAdminUsuarios();
            } catch (error) {
                alert(error.message);
            }
        });
    });
}

async function loadTutorCriarAgendamento() {
    const user = await requireAuth("TUTOR");
    if (!user) return;

    initAppShell({
        header: { mode: "title", title: "Criar agendamento", subtitle: "Agende uma sessão de tutoria com um estudante." }
    });

    const ids = await resolveProfileIds(user);

    if (!ids.tutorId) {
        showError("Perfil de tutor não encontrado. Verifique seu cadastro.");
        return;
    }

    const estudantes = await fetchEstudantes();

    fillSelect("estudanteSelect", estudantes, function (estudante) {
        return formatUsuario(estudante.usuario) + " — " + (estudante.diaDaSemana || "Disponibilidade a combinar");
    }, function (estudante) {
        return estudante.id;
    });

    const form = document.getElementById("agendamentoForm");
    if (!form) return;

    // Preenche data mínima como hoje
    const dataInput = document.getElementById("data");
    if (dataInput) {
        dataInput.min = new Date().toISOString().split("T")[0];
    }

    form.addEventListener("submit", async function (event) {
        event.preventDefault();

        const estudanteId = document.getElementById("estudanteSelect").value;
        const data = document.getElementById("data").value;
        const hora = document.getElementById("hora").value;
        const observacoes = document.getElementById("observacoes").value.trim();

        if (!data || !hora) {
            showFeedback("feedback", "Informe data e hora da sessão.", "error");
            return;
        }

        try {
            await createAgendamento({
                status: "PENDENTE",
                data: data,
                hora: hora,
                observacoes: observacoes || null,
                tutor: { id: Number(ids.tutorId) },
                estudante: { id: Number(estudanteId) }
            });

            showFeedback("feedback", "Agendamento criado com sucesso!", "success");
            form.reset();
            if (dataInput) dataInput.min = new Date().toISOString().split("T")[0];
        } catch (error) {
            showFeedback("feedback", error.message, "error");
        }
    });
}

document.addEventListener("DOMContentLoaded", function () {
    const page = document.body.dataset.page;

    if (!page) {
        return;
    }

    const loader = PAGE_LOADERS[page];

    if (loader) {
        loader().catch(function (error) {
            showError(error.message || "Não foi possível carregar a página.");
        });
    }
});
