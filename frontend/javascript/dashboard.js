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
    "tutor-disciplinas": loadTutorDisciplinas,
    "admin-dashboard": loadAdminDashboard,
    "admin-aprovacoes": loadAdminAprovacoes,
    "admin-disciplinas": loadAdminDisciplinas,
    "admin-avaliacoes": loadAdminAvaliacoes,
    "admin-usuarios": loadAdminUsuarios
};


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

    await anexarLinksReuniao(agendamentos.slice(0, 5));
    renderEstudanteAgendamentoCards("agendamentosPreview", agendamentos.slice(0, 5));
    renderReviewCards("avaliacoesPreview", avaliacoes, { limit: 2, pessoa: "tutor" });
}

// Guia "Tutores": lista de disciplinas; ao clicar em uma, abre um popup com
// os tutores dela, avaliacoes, horarios e o botao de solicitar.
// ?disciplina=... na URL abre a pagina ja com o popup daquela disciplina.
async function loadEstudanteBuscarTutores() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    initAppShell({
        notifCount: 0,
        header: { mode: "search", title: "Disciplinas", subtitle: "Carregando..." }
    });

    let disciplinas = [];
    let filtro = "";
    const mostrar = function () {
        const lista = disciplinas.filter(function (d) {
            return d.nome.toLowerCase().includes(filtro)
                || d.nomesTutores.some(function (n) { return n.toLowerCase().includes(filtro); });
        });
        updatePageSubtitle(lista.length === 1 ? "1 disciplina com tutoria" : lista.length + " disciplinas com tutoria");
        renderDisciplinaCards("tutoresLista", lista);
    };
    // Recarrega os contadores ("3 horários livres") depois de solicitar/cancelar no popup
    const carregarDisciplinas = async function () {
        const [tutores, horarios] = await Promise.all([fetchTutores(), safeArray(fetchSessoesDisponiveis())]);
        disciplinas = agruparPorDisciplina(tutores, horarios);
        mostrar();
    };

    showLoader("Carregando disciplinas...");
    await carregarDisciplinas();
    hideLoader();

    bindSearchInput(function (query) {
        filtro = query;
        mostrar();
    });

    const popup = criarPopupDisciplina(carregarDisciplinas);

    // O card continua sendo um link (?disciplina=...): abrir em nova aba funciona
    document.getElementById("tutoresLista").addEventListener("click", function (event) {
        const card = event.target.closest("a.disciplina-card");
        if (!card || event.ctrlKey || event.metaKey || event.shiftKey || event.button !== 0) return;
        event.preventDefault();
        popup.abrir(new URL(card.href).searchParams.get("disciplina"));
    });

    const inicial = getQueryParam("disciplina");
    if (inicial) popup.abrir(inicial);
}

function criarPopupDisciplina(aoFecharComMudancas) {
    const overlay = document.getElementById("disciplinaModal");
    const titulo = document.getElementById("disciplinaModalTitle");
    const subtitulo = document.getElementById("disciplinaModalSubtitle");
    const conteudo = document.getElementById("disciplinaDetalhe");
    const feedback = document.getElementById("disciplinaFeedback");
    let houveMudanca = false;
    let quemAbriu = null;

    const fechar = function () {
        if (!overlay.classList.contains("open")) return;
        overlay.classList.remove("open");
        overlay.setAttribute("aria-hidden", "true");
        document.body.classList.remove("modal-aberto");
        history.replaceState(null, "", location.pathname);
        if (quemAbriu) quemAbriu.focus();
        if (houveMudanca) {
            houveMudanca = false;
            aoFecharComMudancas().catch(function () { /* a lista antiga continua na tela */ });
        }
    };

    const abrir = async function (disciplina) {
        quemAbriu = document.activeElement;
        titulo.textContent = disciplina;
        subtitulo.textContent = "Carregando...";
        feedback.style.display = "none";
        conteudo.innerHTML = "<div class=\"loading-state\">Carregando tutores e horários...</div>";
        overlay.classList.add("open");
        overlay.setAttribute("aria-hidden", "false");
        document.body.classList.add("modal-aberto");
        document.getElementById("disciplinaModalClose").focus();
        history.replaceState(null, "", "?disciplina=" + encodeURIComponent(disciplina));

        try {
            const [tutores, horarios] = await Promise.all([fetchTutores(), fetchHorariosPorDisciplina(disciplina)]);
            const chave = normalizarDisciplina(disciplina);
            const daDisciplina = tutores.filter(function (t) { return tutorAtende(t, disciplina); });
            // A solicitacao vai com a disciplina do popup: o tutor pode atender varias
            const escolhida = daDisciplina.length
                ? daDisciplina[0].disciplinas.find(function (d) { return normalizarDisciplina(d.nome) === chave; })
                : null;
            const disciplinaId = escolhida ? escolhida.id : null;

            subtitulo.textContent = plural(daDisciplina.length, "tutor", "tutores") + " · "
                + plural(horarios.length, "horário disponível", "horários disponíveis");

            renderDetalheDisciplina("disciplinaDetalhe", daDisciplina, horarios,
                async function (horarioId, modalidade) {
                    await solicitarAgendamento(horarioId, modalidade, disciplinaId);
                    houveMudanca = true;
                    showFeedback("disciplinaFeedback", modalidade === "ONLINE"
                        ? "Solicitação online enviada! Quando o tutor confirmar, o link da reunião aparece na guia \"Solicitações\"."
                        : "Solicitação enviada! O tutor precisa confirmar. Acompanhe na guia \"Solicitações\".", "success");
                },
                async function (horarioId) {
                    if (!confirmarCancelamentoDoEstudante()) return false;
                    await cancelarAgendamento(horarioId);
                    houveMudanca = true;
                    showFeedback("disciplinaFeedback", "Agendamento cancelado. O horário voltou a ficar disponível.", "success");
                },
                "disciplinaFeedback");
        } catch (err) {
            subtitulo.textContent = "";
            conteudo.innerHTML = "";
            showFeedback("disciplinaFeedback", err.message || "Não foi possível carregar a disciplina.", "error");
        }
    };

    document.getElementById("disciplinaModalClose").addEventListener("click", fechar);
    // Clique fora da caixa (no fundo escurecido) fecha
    overlay.addEventListener("click", function (event) {
        if (event.target === overlay) fechar();
    });
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") fechar();
    });

    return { abrir: abrir, fechar: fechar };
}

// Busca o link das tutorias online ja confirmadas (o backend so libera nesse caso)
async function anexarLinksReuniao(agendamentos) {
    const confirmadasOnline = (agendamentos || []).filter(function (a) {
        return a.modalidade === "ONLINE" && normalizeStatus(a.status) === "CONFIRMADO";
    });
    await Promise.all(confirmadasOnline.map(function (a) {
        return fetchLinkReuniao(a.id)
            .then(function (r) { a.linkReuniao = r && r.linkReuniao; })
            .catch(function () { /* sem link: o card aparece sem o botao */ });
    }));
    return agendamentos;
}

function confirmarCancelamentoDoEstudante() {
    return confirm("Cancelar este agendamento? O horário voltará a ficar disponível para outros estudantes.\n\n"
        + "Lembre-se: o cancelamento só pode ser feito até o dia anterior à tutoria.");
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
        await solicitarAgendamento(sessaoId);
        showFeedback("feedback", "Solicitação enviada com sucesso!", "success");
    });
}

async function loadEstudanteMeusAgendamentos() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    initAppShell({
        notifCount: 0,
        header: { mode: "title", title: "Minhas solicitações", subtitle: "Acompanhe o status das suas tutorias." }
    });

    showLoader("Carregando agendamentos...");
    const agendamentos = await anexarLinksReuniao(await fetchMinhaAgenda());
    hideLoader();

    renderEstudanteAgendamentoCards("agendamentos", agendamentos, {
        onCancelar: async function (id) {
            if (!confirmarCancelamentoDoEstudante()) return;
            await cancelarAgendamento(id);
            await loadEstudanteMeusAgendamentos();
        }
    });
}

async function loadEstudanteAvaliacoes() {
    const user = await requireAuth("ESTUDANTE");
    if (!user) return;

    const ids = await resolveProfileIds(user);

    initAppShell({
        notifCount: 0,
        header: { mode: "title", title: "Avaliações", subtitle: "Consulte e registre suas avaliações." }
    });

    if (!ids.estudanteId) {
        renderReviewCards("avaliacoesLista", []);
        document.getElementById("tutoresAvaliaveis").innerHTML =
            "<div class=\"error-state\">Perfil de estudante não encontrado. Verifique seu cadastro.</div>";
        return;
    }

    const [avaliacoes, agenda] = await Promise.all([
        safeArray(fetchEstudanteAvaliacoes(ids.estudanteId)),
        safeArray(fetchMinhaAgenda())
    ]);

    const agendaPorId = new Map((agenda || []).map(function (a) { return [a.id, a]; }));
    const mostrarAvaliacoes = function (lista) {
        // Cada avaliacao mostra de qual tutoria e (data da sessao)
        lista.forEach(function (av) {
            const tutoria = agendaPorId.get(av.agendamentoId);
            if (tutoria) av.dataTutoria = tutoria.data;
        });
        renderReviewCards("avaliacoesLista", avaliacoesRecentesPrimeiro(lista), { pessoa: "tutor" });
    };
    mostrarAvaliacoes(avaliacoes);

    const formulario = criarFormularioAvaliacao({
        agenda: agenda,
        avaliadas: idsDasTutoriasAvaliadas(avaliacoes),
        tutorInicial: Number(getQueryParam("tutorId")) || null,
        aoEnviar: async function (tutoria, nota, comentario) {
            // Autor, tutor, data e hora sao definidos pelo backend
            await createAvaliacao({ nota: nota, comentario: comentario, agendamento: { id: tutoria.id } });
            mostrarAvaliacoes(await safeArray(fetchEstudanteAvaliacoes(ids.estudanteId)));
        }
    });
    formulario.iniciar();
}

// Um "story" por tutor das tutorias do estudante:
//  - pendentes: ja aconteceram (confirmada/realizada e ja iniciada) e ainda
//    nao foram avaliadas, da mais antiga para a mais nova -> pode avaliar
//  - proxima: a tutoria agendada mais proxima (pendente ou confirmada, no
//    futuro) -> o perfil aparece bloqueado ate ela acontecer
//  - sem nenhuma das duas: tudo ja avaliado
// Ordem: pode avaliar, depois bloqueados, depois avaliados. O backend confere tudo de novo.
function tutoresComTutoriaFeita(agenda, avaliadas) {
    const agora = new Date();
    const porTutor = new Map();

    (agenda || []).forEach(function (a) {
        const status = normalizeStatus(a.status);
        if (!a.tutor || (status !== "PENDENTE" && status !== "CONFIRMADO" && status !== "REALIZADO")) return;
        const inicio = new Date(a.data + "T" + (a.hora || "00:00"));
        if (isNaN(inicio)) return;

        if (!porTutor.has(a.tutor.id)) {
            porTutor.set(a.tutor.id, { tutor: a.tutor, pendentes: [], proxima: null, ultimaTutoria: 0, teveTutoria: false });
        }
        const item = porTutor.get(a.tutor.id);
        const aconteceu = inicio <= agora && status !== "PENDENTE";

        if (aconteceu) {
            item.teveTutoria = true;
            if (inicio > item.ultimaTutoria) item.ultimaTutoria = inicio;
            if (!avaliadas.has(a.id)) item.pendentes.push({ tutoria: a, inicio: inicio });
        } else if (inicio > agora && (!item.proxima || inicio < item.proxima.inicio)) {
            item.proxima = { tutoria: a, inicio: inicio };
        }
    });

    const grupo = function (item) {
        return item.pendentes.length ? 0 : item.proxima ? 1 : 2;
    };

    return Array.from(porTutor.values())
        .filter(function (item) { return item.teveTutoria || item.proxima; })
        .map(function (item) {
            item.pendentes = item.pendentes
                .sort(function (x, y) { return x.inicio - y.inicio; })
                .map(function (p) { return p.tutoria; });
            return item;
        })
        .sort(function (x, y) {
            return grupo(x) - grupo(y)
                || (grupo(x) === 1 ? x.proxima.inicio - y.proxima.inicio : y.ultimaTutoria - x.ultimaTutoria);
        });
}

// Cabe embaixo do avatar: "Libera 10/11" ou "A confirmar" (tutor ainda nao
// aceitou). A explicacao completa fica no title/aria-label do story.
function rotuloTutoriaFutura(proxima) {
    if (normalizeStatus(proxima.tutoria.status) === "PENDENTE") return "A confirmar";
    return "Libera " + proxima.tutoria.data.split("-").reverse().slice(0, 2).join("/");
}

function idsDasTutoriasAvaliadas(avaliacoes) {
    return new Set((avaliacoes || [])
        .map(function (a) { return a.agendamentoId; })
        .filter(Boolean));
}

function avaliacoesRecentesPrimeiro(avaliacoes) {
    return (avaliacoes || []).slice().sort(function (a, b) {
        const ka = (a.dataAvaliacao || "") + (a.horaAvaliacao || "");
        const kb = (b.dataAvaliacao || "") + (b.horaAvaliacao || "");
        return kb.localeCompare(ka) || (b.id || 0) - (a.id || 0);
    });
}

const NOTA_LEGENDA = ["", "Muito ruim", "Ruim", "Regular", "Boa", "Excelente!"];

// Formulario "Avaliar tutoria": escolher o tutor (avatares estilo stories) e
// tocar nas estrelas. Cada tutoria e avaliada uma vez; se houver mais de uma
// pendente com o mesmo tutor, avalia-se da mais antiga para a mais nova.
// Os listeners sao ligados uma unica vez.
function criarFormularioAvaliacao(config) {
    const form = document.getElementById("avaliacaoForm");
    const stories = document.getElementById("tutoresAvaliaveis");
    const notaPasso = document.getElementById("notaPasso");
    const legenda = document.getElementById("notaLegenda");
    const qualTutoria = document.getElementById("qualTutoria");
    const rating = document.getElementById("ratingInput");
    const ratingLabel = document.getElementById("ratingLabel");
    const enviar = document.getElementById("avaliarEnviar");
    const comentario = document.getElementById("comentario");
    const contador = document.getElementById("comentarioContador");
    const avaliadas = config.avaliadas;

    const atualizarContador = function () {
        contador.textContent = comentario.value.length + "/" + comentario.maxLength;
    };
    comentario.addEventListener("input", atualizarContador);

    let tutores = [];
    let tutorEscolhido = null;
    let tutoriaAtual = null;
    let nota = 0;

    const primeiroNome = function (tutor) {
        return formatUsuario(tutor.usuario).split(/\s+/)[0];
    };

    const pintarEstrelas = function (valor, previa) {
        rating.querySelectorAll(".rating-star").forEach(function (estrela, i) {
            estrela.classList.toggle("on", i < valor);
            estrela.classList.toggle("previa", Boolean(previa) && i < valor);
        });
    };

    const atualizarTextoNota = function (valor) {
        ratingLabel.textContent = valor
            ? valor + " de 5 · " + NOTA_LEGENDA[valor]
            : tutorEscolhido ? "Toque nas estrelas para dar sua nota" : "Escolha um tutor acima";
        ratingLabel.classList.toggle("ativo", Boolean(valor));
    };

    const atualizarBotao = function () {
        enviar.disabled = !(tutorEscolhido && nota);
        enviar.textContent = tutorEscolhido
            ? "Enviar avaliação para " + primeiroNome(tutorEscolhido)
            : "Enviar avaliação";
    };

    const desenharStories = function () {
        tutores = tutoresComTutoriaFeita(config.agenda, avaliadas);

        if (!tutores.length) {
            stories.innerHTML = "<div class=\"empty-state\">"
                + "Os tutores das suas tutorias aparecem aqui assim que você agendar uma."
                + "</div>";
            return;
        }

        stories.innerHTML = tutores.map(function (item) {
            const tutor = item.tutor;
            const nome = formatUsuario(tutor.usuario);
            const pendentes = item.pendentes.length;
            const bloqueado = !pendentes && Boolean(item.proxima);
            const tudoAvaliado = !pendentes && !item.proxima;
            const situacao = pendentes ? plural(pendentes, "tutoria para avaliar", "tutorias para avaliar")
                : bloqueado ? (normalizeStatus(item.proxima.tutoria.status) === "PENDENTE" ? "aguardando o tutor confirmar; " : "")
                    + "avaliação liberada depois da tutoria de " + formatarHorario(item.proxima.tutoria)
                : "todas as tutorias já avaliadas";
            const disciplina = pendentes ? disciplinaDaSessao(item.pendentes[0]) : disciplinasDoTutor(tutor);
            const meta = pendentes ? disciplina
                : bloqueado ? rotuloTutoriaFutura(item.proxima)
                : "Avaliado";
            const dica = nome + " — " + disciplina + " · " + situacao;
            const classe = tudoAvaliado ? " avaliado" : bloqueado ? " bloqueado" : "";
            // A cor do avatar segue o tutor: nao muda quando a fila reordena
            return "<label class=\"story" + classe + "\" title=\"" + escapeHTML(dica) + "\">"
                + "<input class=\"sr-only\" type=\"radio\" name=\"tutorAvaliado\" value=\"" + escapeHTML(tutor.id) + "\""
                + (pendentes ? "" : " disabled") + " aria-label=\"" + escapeHTML(dica) + "\">"
                + "<span class=\"story-ring\"><span class=\"avatar " + avatarColorClass(tutor.id) + "\">" + escapeHTML(getInitials(nome)) + "</span>"
                + (pendentes > 1 ? "<span class=\"story-contador\" aria-hidden=\"true\">" + pendentes + "</span>" : "")
                + (bloqueado ? "<span class=\"story-cadeado\" aria-hidden=\"true\">" + icon("lock") + "</span>" : "")
                + "<span class=\"story-check\" aria-hidden=\"true\">" + icon("check") + "</span></span>"
                + "<span class=\"story-nome\">" + escapeHTML(primeiroNome(tutor)) + "</span>"
                + "<span class=\"story-meta\">" + escapeHTML(meta) + "</span>"
                + "</label>";
        }).join("");
    };

    const escolherTutor = function (tutorId) {
        const item = tutores.find(function (t) { return t.tutor.id === tutorId && t.pendentes.length; });
        if (!item) return false;
        tutorEscolhido = item.tutor;
        tutoriaAtual = item.pendentes[0];
        const radio = stories.querySelector("input[value=\"" + tutorId + "\"]");
        if (radio) radio.checked = true;

        notaPasso.disabled = false;
        legenda.textContent = "Como foi a tutoria com " + primeiroNome(item.tutor) + "?";
        const restantes = item.pendentes.length - 1;
        const disciplinaDaTutoria = disciplinaDaSessao(tutoriaAtual);
        qualTutoria.textContent = (disciplinaDaTutoria ? disciplinaDaTutoria + " · " : "")
            + formatarHorario(tutoriaAtual)
            + (restantes ? " · depois, mais " + plural(restantes, "tutoria", "tutorias") : "");
        qualTutoria.hidden = false;
        atualizarTextoNota(nota);
        atualizarBotao();
        return true;
    };

    const escolherNota = function (valor) {
        nota = valor;
        const radio = rating.querySelector("input[value=\"" + valor + "\"]");
        if (radio) radio.checked = true;
        pintarEstrelas(valor, false);
        atualizarTextoNota(valor);
        atualizarBotao();
    };

    const limpar = function () {
        tutorEscolhido = null;
        tutoriaAtual = null;
        nota = 0;
        form.reset();
        notaPasso.disabled = true;
        legenda.textContent = "Como foi a tutoria?";
        qualTutoria.hidden = true;
        atualizarContador();
        pintarEstrelas(0, false);
        atualizarTextoNota(0);
        atualizarBotao();
    };

    // 5 estrelas como radios: funcionam com Tab + setas e leitores de tela
    rating.innerHTML = [1, 2, 3, 4, 5].map(function (n) {
        return "<label class=\"rating-star\" title=\"" + NOTA_LEGENDA[n] + "\">"
            + "<input class=\"sr-only\" type=\"radio\" name=\"nota\" value=\"" + n + "\">"
            + "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\"><path d=\"M12 2.8l2.84 5.76 6.36.92-4.6 4.48 1.09 6.33L12 17.3l-5.69 2.99 1.09-6.33-4.6-4.48 6.36-.92z\"/></svg>"
            + "<span class=\"sr-only\">" + n + " de 5 — " + NOTA_LEGENDA[n] + "</span>"
            + "</label>";
    }).join("");

    rating.addEventListener("change", function (event) {
        if (event.target.name === "nota") escolherNota(Number(event.target.value));
    });
    // Previa ao passar o mouse: mostra quantas estrelas e o significado da nota
    rating.addEventListener("mouseover", function (event) {
        const estrela = event.target.closest(".rating-star");
        if (!estrela || notaPasso.disabled) return;
        const valor = Number(estrela.querySelector("input").value);
        pintarEstrelas(valor, true);
        atualizarTextoNota(valor);
    });
    rating.addEventListener("mouseleave", function () {
        pintarEstrelas(nota, false);
        atualizarTextoNota(nota);
    });

    stories.addEventListener("change", function (event) {
        if (event.target.name === "tutorAvaliado") escolherTutor(Number(event.target.value));
    });

    form.addEventListener("submit", async function (event) {
        event.preventDefault();
        if (!tutoriaAtual || !nota) return;

        const tutor = tutorEscolhido;
        const tutoria = tutoriaAtual;
        const textoBotao = enviar.textContent;
        enviar.disabled = true;
        enviar.textContent = "Enviando...";

        try {
            await config.aoEnviar(tutoria, nota, comentario.value.trim() || null);
            avaliadas.add(tutoria.id);
            limpar();
            desenharStories();
            // Ainda ha outra tutoria com o mesmo tutor? Ja deixa escolhida
            const temMais = escolherTutor(tutor.id);
            showFeedback("feedback", "Obrigado! Sua avaliação para " + formatUsuario(tutor.usuario) + " foi registrada."
                + (temMais ? " Falta avaliar outra tutoria com " + primeiroNome(tutor) + "." : ""), "success");
        } catch (error) {
            enviar.disabled = false;
            enviar.textContent = textoBotao;
            showFeedback("feedback", error.message || "Não foi possível enviar a avaliação.", "error");
        }
    });

    return {
        iniciar: function () {
            desenharStories();
            limpar();
            // Com um so tutor para avaliar (ou ?tutorId= na URL), ele ja vem escolhido
            const comPendencia = tutores.filter(function (t) { return t.pendentes.length; });
            const inicial = config.tutorInicial
                || (comPendencia.length === 1 ? comPendencia[0].tutor.id : null);
            if (inicial) escolherTutor(inicial);
        }
    };
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

    initAppShell({
        header: { mode: "title", title: "Agenda", subtitle: "Clique em um horário vazio para abrir uma sessão de 1 hora." }
    });

    showLoader("Carregando agenda...");
    // Sem safeArray: se a API falhar, o tutor precisa ver o erro, e nao uma agenda vazia
    let agendamentos = [];
    try {
        agendamentos = await fetchMinhaAgenda();
    } catch (err) {
        showFeedback("feedback", "Não foi possível carregar sua agenda: " + err.message, "error");
    }
    hideLoader();

    if (typeof initAgenda === "function") {
        initAgenda(agendamentos);
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

    showLoader("Carregando dashboard...");

    const results = await Promise.all([
        fetchUsuarios(),
        fetchEstudantes(),
        fetchTutores(),
        fetchAgendamentos(),
        fetchCoordenadores(),
        fetchCadastrosPendentes()
    ]);

    hideLoader();
    // O sino do menu mostra quantos cadastros aguardam aprovacao
    initAppShell({ notifCount: results[5].length });
    setText("statUsuarios", results[0].length);
    setText("statEstudantes", results[1].length);
    setText("statTutores", results[2].length);
    setText("statAgendamentos", results[3].length);
    setText("statCoordenadores", results[4].length);
    renderAgendamentosTable("agendamentos", results[3].slice(0, 8));
    renderUsuariosList("usuariosLista", results[0].slice(0, 5));
}

async function loadAdminAprovacoes() {
    const user = await requireAuth("ADMIN");
    if (!user) return;

    showLoader("Carregando cadastros pendentes...");
    const [pendentes, pedidos] = await Promise.all([fetchCadastrosPendentes(), safeArray(fetchPedidosDisciplina())]);
    hideLoader();

    initAppShell({
        notifCount: pendentes.length + pedidos.length,
        header: {
            mode: "title",
            title: "Aprovações",
            subtitle: "Cadastros novos e disciplinas pedidas pelos tutores só valem depois de aprovados."
        }
    });

    renderPedidosDisciplina(pedidos);

    const table = document.getElementById("pendentesTable");
    if (!table) return;

    if (!pendentes.length) {
        table.innerHTML = "<tbody><tr><td><div class=\"empty-state\">Nenhum cadastro aguardando aprovação.</div></td></tr></tbody>";
        return;
    }

    const rows = pendentes.map(function (usuario) {
        // Tutor: a disciplina escolhida no cadastro vale junto com a aprovacao da conta
        const disciplinas = Array.isArray(usuario.disciplinas) && usuario.disciplinas.length
            ? usuario.disciplinas.join(", ")
            : "—";
        return "<tr>"
            + "<td>#" + escapeHTML(usuario.id) + "</td>"
            + "<td>" + escapeHTML(usuario.nome) + "</td>"
            + "<td>" + escapeHTML(usuario.email) + "</td>"
            + "<td><span class=\"badge\">" + escapeHTML(usuario.tipo) + "</span></td>"
            + "<td>" + escapeHTML(disciplinas) + "</td>"
            + "<td><div class=\"admin-actions\">"
            + "<button class=\"btn sm\" type=\"button\" data-action=\"aprovar-cadastro\" data-usuario-id=\"" + escapeHTML(usuario.id) + "\">" + icon("check") + " Aprovar</button>"
            + "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"recusar-cadastro\" data-usuario-id=\"" + escapeHTML(usuario.id) + "\">Recusar</button>"
            + "</div></td></tr>";
    }).join("");

    table.innerHTML = "<thead><tr><th>ID</th><th>Nome</th><th>Email</th><th>Perfil</th><th>Disciplina</th><th>Ações</th></tr></thead><tbody>" + rows + "</tbody>";

    table.querySelectorAll("[data-action='aprovar-cadastro']").forEach(function (button) {
        button.addEventListener("click", async function () {
            try {
                await aprovarCadastro(button.getAttribute("data-usuario-id"));
                await loadAdminAprovacoes();
            } catch (error) {
                showFeedback("feedback", error.message, "error");
            }
        });
    });

    table.querySelectorAll("[data-action='recusar-cadastro']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const id = button.getAttribute("data-usuario-id");
            if (!confirm("Recusar o cadastro #" + id + "? A pessoa não conseguirá entrar no sistema.")) return;

            try {
                await recusarCadastro(id);
                await loadAdminAprovacoes();
            } catch (error) {
                showFeedback("feedback", error.message, "error");
            }
        });
    });
}

// Tabela "Pedidos de disciplina" da guia Aprovacoes
function renderPedidosDisciplina(pedidos) {
    const table = document.getElementById("pedidosDisciplinaTable");
    if (!table) return;

    if (!pedidos.length) {
        table.innerHTML = "<tbody><tr><td><div class=\"empty-state\">Nenhum pedido de disciplina aguardando aprovação.</div></td></tr></tbody>";
        return;
    }

    const rows = pedidos.map(function (pedido) {
        const quando = pedido.solicitadaEm ? pedido.solicitadaEm.substring(0, 10).split("-").reverse().join("/") : "--";
        return "<tr>"
            + "<td>" + escapeHTML(pedido.tutorNome) + "</td>"
            + "<td>" + escapeHTML(pedido.disciplina.nome) + "</td>"
            + "<td>" + escapeHTML(quando) + "</td>"
            + "<td><div class=\"admin-actions\">"
            + "<button class=\"btn sm\" type=\"button\" data-action=\"aprovar-pedido\" data-pedido-id=\"" + escapeHTML(pedido.id) + "\">" + icon("check") + " Aprovar</button>"
            + "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"recusar-pedido\" data-pedido-id=\"" + escapeHTML(pedido.id) + "\" data-descricao=\""
            + escapeHTML(pedido.disciplina.nome + " para " + pedido.tutorNome) + "\">Recusar</button>"
            + "</div></td></tr>";
    }).join("");

    table.innerHTML = "<thead><tr><th>Tutor</th><th>Disciplina pedida</th><th>Pedido em</th><th>Ações</th></tr></thead><tbody>" + rows + "</tbody>";

    table.querySelectorAll("[data-action='aprovar-pedido'], [data-action='recusar-pedido']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const id = button.getAttribute("data-pedido-id");
            const recusar = button.getAttribute("data-action") === "recusar-pedido";
            if (recusar && !confirm("Recusar " + button.getAttribute("data-descricao") + "?")) return;

            button.disabled = true;
            try {
                await (recusar ? recusarPedidoDisciplina(id) : aprovarPedidoDisciplina(id));
                await loadAdminAprovacoes();
            } catch (error) {
                button.disabled = false;
                showFeedback("feedback", error.message, "error");
            }
        });
    });
}

// ── Guia "Disciplinas" do tutor ──────────────────────────────────────────

const SITUACAO_DISCIPLINA = {
    ATIVA: { badge: "success", texto: "Ativa", acao: "Remover" },
    PENDENTE: { badge: "warning", texto: "Aguardando aprovação", acao: "Cancelar pedido" },
    RECUSADA: { badge: "danger", texto: "Recusada pela coordenação", acao: "Remover" }
};

async function loadTutorDisciplinas() {
    const user = await requireAuth("TUTOR");
    if (!user) return;

    initAppShell({
        header: {
            mode: "title",
            title: "Disciplinas",
            subtitle: "Os estudantes encontram você nas disciplinas ativas. Seus horários livres aparecem em todas elas."
        }
    });

    const tabela = document.getElementById("minhasDisciplinasTable");
    const select = document.getElementById("novaDisciplina");
    const form = document.getElementById("pedirDisciplinaForm");

    const recarregar = async function () {
        const [minhas, todas] = await Promise.all([fetchMinhasDisciplinas(), fetchDisciplinas()]);
        hideLoader();

        tabela.innerHTML = "<thead><tr><th>Disciplina</th><th>Situação</th><th>Ações</th></tr></thead><tbody>"
            + minhas.map(function (vinculo) {
                const situacao = SITUACAO_DISCIPLINA[vinculo.status] || SITUACAO_DISCIPLINA.PENDENTE;
                return "<tr>"
                    + "<td>" + escapeHTML(vinculo.disciplina.nome) + "</td>"
                    + "<td><span class=\"badge " + situacao.badge + "\">" + escapeHTML(situacao.texto) + "</span></td>"
                    + "<td><button class=\"btn outline-danger sm\" type=\"button\" data-vinculo-id=\"" + escapeHTML(vinculo.id) + "\""
                    + " data-nome=\"" + escapeHTML(vinculo.disciplina.nome) + "\" data-status=\"" + escapeHTML(vinculo.status) + "\">"
                    + escapeHTML(situacao.acao) + "</button></td>"
                    + "</tr>";
            }).join("")
            + "</tbody>";

        // So aparece no seletor o que ainda nao esta na lista do tutor
        const jaTem = new Set(minhas.map(function (v) { return v.disciplina.id; }));
        const disponiveis = todas.filter(function (d) { return !jaTem.has(d.id); });
        select.innerHTML = disponiveis.length
            ? "<option value=\"\">Selecione</option>" + disponiveis.map(function (d) {
                return "<option value=\"" + escapeHTML(d.id) + "\">" + escapeHTML(d.nome) + "</option>";
            }).join("")
            : "<option value=\"\">Você já tem todas as disciplinas da lista</option>";
        select.disabled = !disponiveis.length;
        form.querySelector("button[type='submit']").disabled = !disponiveis.length;
    };

    tabela.addEventListener("click", async function (event) {
        const button = event.target.closest("button[data-vinculo-id]");
        if (!button) return;
        const nome = button.getAttribute("data-nome");
        const pergunta = button.getAttribute("data-status") === "PENDENTE"
            ? "Cancelar o pedido de " + nome + "?"
            : button.getAttribute("data-status") === "ATIVA"
                ? "Deixar de atender " + nome + "? Você some da busca dessa disciplina; tutorias já marcadas continuam."
                : "Remover " + nome + " da lista?";
        if (!confirm(pergunta)) return;

        button.disabled = true;
        try {
            await removerMinhaDisciplina(button.getAttribute("data-vinculo-id"));
            showFeedback("feedback", nome + " saiu da sua lista.", "success");
            await recarregar();
        } catch (error) {
            button.disabled = false;
            showFeedback("feedback", error.message, "error");
        }
    });

    form.addEventListener("submit", async function (event) {
        event.preventDefault();
        if (!select.value) return;
        const nome = select.options[select.selectedIndex].textContent;
        const botao = form.querySelector("button[type='submit']");
        botao.disabled = true;
        try {
            await pedirDisciplina(Number(select.value));
            showFeedback("feedback", "Pedido de " + nome + " enviado. Ela aparece para os estudantes quando a coordenação aprovar.", "success");
            await recarregar();
        } catch (error) {
            showFeedback("feedback", error.message, "error");
            botao.disabled = false;
        }
    });

    await recarregar();
}

// ── Guia "Disciplinas" da coordenacao ────────────────────────────────────

async function loadAdminDisciplinas() {
    const user = await requireAuth("ADMIN");
    if (!user) return;

    initAppShell({
        header: {
            mode: "title",
            title: "Disciplinas",
            subtitle: "Lista de onde os tutores escolhem as disciplinas que atendem."
        }
    });

    const tabela = document.getElementById("disciplinasTable");
    const form = document.getElementById("novaDisciplinaForm");
    const campoNome = document.getElementById("nomeDisciplina");

    const recarregar = async function () {
        const disciplinas = await fetchDisciplinas();
        hideLoader();

        if (!disciplinas.length) {
            tabela.innerHTML = "<tbody><tr><td><div class=\"empty-state\">Nenhuma disciplina cadastrada.</div></td></tr></tbody>";
            return;
        }
        tabela.innerHTML = "<thead><tr><th>Disciplina</th><th>Ações</th></tr></thead><tbody>"
            + disciplinas.map(function (d) {
                return "<tr>"
                    + "<td>" + escapeHTML(d.nome) + "</td>"
                    + "<td><div class=\"admin-actions\">"
                    + "<button class=\"btn secondary sm\" type=\"button\" data-action=\"renomear\" data-id=\"" + escapeHTML(d.id) + "\" data-nome=\"" + escapeHTML(d.nome) + "\">Renomear</button>"
                    + "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"excluir\" data-id=\"" + escapeHTML(d.id) + "\" data-nome=\"" + escapeHTML(d.nome) + "\">Excluir</button>"
                    + "</div></td></tr>";
            }).join("")
            + "</tbody>";
    };

    tabela.addEventListener("click", async function (event) {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        const id = button.getAttribute("data-id");
        const nome = button.getAttribute("data-nome");

        try {
            if (button.getAttribute("data-action") === "renomear") {
                const novoNome = prompt("Novo nome para \"" + nome + "\":", nome);
                if (!novoNome || novoNome.trim() === nome) return;
                await renomearDisciplina(id, novoNome.trim());
                showFeedback("feedback", "Disciplina renomeada para " + novoNome.trim() + ".", "success");
            } else {
                if (!confirm("Excluir a disciplina " + nome + "?")) return;
                await excluirDisciplina(id);
                showFeedback("feedback", nome + " foi excluída.", "success");
            }
            await recarregar();
        } catch (error) {
            showFeedback("feedback", error.message, "error");
        }
    });

    form.addEventListener("submit", async function (event) {
        event.preventDefault();
        const nome = campoNome.value.trim();
        if (!nome) return;
        try {
            await criarDisciplina(nome);
            campoNome.value = "";
            showFeedback("feedback", nome + " foi adicionada à lista.", "success");
            await recarregar();
        } catch (error) {
            showFeedback("feedback", error.message, "error");
        }
    });

    await recarregar();
}

// ── Guia "Avaliações" da coordenacao ─────────────────────────────────────

// Uma linha por tutor (media, total e as avaliacoes, mais recentes primeiro).
// Tutores ativos sem nenhuma avaliacao tambem aparecem, no fim da lista.
function agruparAvaliacoesPorTutor(avaliacoes, tutores) {
    const porTutor = new Map();
    const grupo = function (tutor) {
        if (!porTutor.has(tutor.id)) porTutor.set(tutor.id, { tutor: tutor, avaliacoes: [] });
        return porTutor.get(tutor.id);
    };

    (tutores || []).forEach(grupo);
    (avaliacoes || []).forEach(function (av) {
        if (av.tutor) grupo(av.tutor).avaliacoes.push(av);
    });

    return Array.from(porTutor.values())
        .map(function (item) {
            item.avaliacoes = avaliacoesRecentesPrimeiro(item.avaliacoes);
            item.media = Number(computeAverageRating(item.avaliacoes));
            item.nome = formatUsuario(item.tutor.usuario);
            return item;
        })
        .sort(function (a, b) {
            return (b.avaliacoes.length ? 1 : 0) - (a.avaliacoes.length ? 1 : 0)
                || b.media - a.media
                || b.avaliacoes.length - a.avaliacoes.length
                || a.nome.localeCompare(b.nome, "pt-BR");
        });
}

function renderAvaliacoesPorTutor(id, grupos) {
    const target = document.getElementById(id);
    if (!target) return;

    if (!grupos.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhum tutor encontrado.</div>";
        return;
    }

    target.innerHTML = grupos.map(function (item) {
        const total = item.avaliacoes.length;
        const disciplinas = disciplinasDoTutor(item.tutor);
        const ultima = total && item.avaliacoes[0].dataAvaliacao
            ? "Última em " + item.avaliacoes[0].dataAvaliacao.split("-").reverse().join("/")
            : "";
        const nota = total
            ? renderStars(item.media, total)
            : "<span class=\"stars\"><span class=\"review-count\">Sem avaliações ainda</span></span>";
        const corpo = total
            ? reviewCardsHTML(item.avaliacoes, { disciplina: true })
            : "<div class=\"empty-state\">Este tutor ainda não recebeu avaliações.</div>";

        // Com uma unica linha na tela (ex.: busca por nome), ja vem aberta
        return "<details class=\"tutor-avaliacoes\"" + (grupos.length === 1 ? " open" : "") + ">"
            + "<summary>"
            + "<span class=\"avatar " + avatarColorClass(item.tutor.id) + "\">" + escapeHTML(getInitials(item.nome)) + "</span>"
            + "<span class=\"item-info\">"
            + "<span class=\"item-name\">" + escapeHTML(item.nome) + "</span>"
            + "<span class=\"item-meta\">" + escapeHTML([disciplinas, ultima].filter(Boolean).join(" · ")) + "</span>"
            + "</span>"
            + nota
            + "<span class=\"tutor-avaliacoes-seta\" aria-hidden=\"true\"></span>"
            + "</summary>"
            + "<div class=\"tutor-avaliacoes-lista\">" + corpo + "</div>"
            + "</details>";
    }).join("");
}

async function loadAdminAvaliacoes() {
    const user = await requireAuth("ADMIN");
    if (!user) return;

    initAppShell({
        header: { mode: "search", title: "Avaliações por tutor", subtitle: "Carregando..." }
    });

    showLoader("Carregando avaliações...");
    const [avaliacoes, tutores] = await Promise.all([fetchAvaliacoes(), safeArray(fetchTutores())]);
    hideLoader();

    const grupos = agruparAvaliacoesPorTutor(avaliacoes, tutores);
    const mostrar = function (filtro) {
        const lista = grupos.filter(function (item) {
            return !filtro
                || item.nome.toLowerCase().includes(filtro)
                || disciplinasDoTutor(item.tutor).toLowerCase().includes(filtro);
        });
        updatePageSubtitle(plural(lista.length, "tutor", "tutores") + " · "
            + plural(avaliacoes.length, "avaliação no total", "avaliações no total")
            + (avaliacoes.length ? " · média geral " + computeAverageRating(avaliacoes) : ""));
        renderAvaliacoesPorTutor("avaliacoesPorTutor", lista);
    };

    mostrar("");
    bindSearchInput(mostrar);
}

const TIPO_LABEL = { TUTOR: "Tutor", ESTUDANTE: "Estudante", ADMIN: "Administrador" };

// O que acontece ao inativar, para a coordenacao confirmar sabendo das consequencias
const AVISO_INATIVACAO = {
    TUTOR: "Os horários futuros deste tutor serão cancelados, inclusive os já reservados por estudantes. Ele deixa de aparecer na busca.",
    ESTUDANTE: "As reservas futuras deste estudante voltarão a ficar disponíveis para outros estudantes."
};

let filtroTipoUsuario = "TODOS";

async function loadAdminUsuarios() {
    const user = await requireAuth("ADMIN");
    if (!user) return;

    initAppShell({
        header: { mode: "title", title: "Usuários", subtitle: "Visualize, edite, inative ou reative tutores e estudantes." }
    });

    showLoader("Carregando usuários...");
    const usuarios = await fetchUsuarios();
    hideLoader();

    const filtros = document.getElementById("filtroTipo");
    if (filtros) {
        filtros.querySelectorAll("[data-tipo]").forEach(function (btn) {
            btn.classList.toggle("active", btn.dataset.tipo === filtroTipoUsuario);
            btn.onclick = function () {
                filtroTipoUsuario = btn.dataset.tipo;
                loadAdminUsuarios();
            };
        });
    }

    const table = document.getElementById("usuariosTable");
    if (!table) return;

    const visiveis = usuarios.filter(function (u) {
        return filtroTipoUsuario === "TODOS" || u.tipo === filtroTipoUsuario;
    });

    if (!visiveis.length) {
        table.innerHTML = "<tbody><tr><td colspan=\"6\"><div class=\"empty-state\">Nenhum usuário encontrado.</div></td></tr></tbody>";
        return;
    }

    const rows = visiveis.map(function (usuario) {
        const gerenciavel = usuario.tipo !== "ADMIN" && String(usuario.id) !== String(user.id);
        const id = escapeHTML(usuario.id);
        let acaoConta = "";
        if (gerenciavel && usuario.status === "ATIVO") {
            acaoConta = "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"inativar-usuario\" data-usuario-id=\"" + id + "\">Inativar</button>";
        } else if (gerenciavel && usuario.status === "INATIVO") {
            acaoConta = "<button class=\"btn secondary sm\" type=\"button\" data-action=\"reativar-usuario\" data-usuario-id=\"" + id + "\">Reativar</button>"
                + "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"anonimizar-usuario\" data-usuario-id=\"" + id + "\">Apagar dados</button>";
        }
        // Conta anonimizada nao e editavel: devolver nome ou e-mail desfaria o pedido do titular
        const botaoEditar = usuario.status === "ANONIMIZADO"
            ? ""
            : "<button class=\"btn secondary sm\" type=\"button\" data-action=\"edit-usuario\" data-usuario-id=\"" + id + "\">Editar</button>";
        return "<tr>"
            + "<td>#" + escapeHTML(usuario.id) + "</td>"
            + "<td>" + escapeHTML(usuario.nome) + "</td>"
            + "<td>" + escapeHTML(usuario.email) + "</td>"
            + "<td><span class=\"badge" + (usuario.tipo === "ADMIN" ? " neutral" : "") + "\">" + escapeHTML(TIPO_LABEL[usuario.tipo] || usuario.tipo) + "</span></td>"
            + "<td>" + usuarioStatusBadge(usuario.status) + "</td>"
            + "<td><div class=\"admin-actions\">"
            + botaoEditar
            + acaoConta
            + "</div></td></tr>";
    }).join("");

    table.innerHTML = "<thead><tr><th>ID</th><th>Nome</th><th>Email</th><th>Tipo</th><th>Status</th><th>Ações</th></tr></thead><tbody>" + rows + "</tbody>";

    function porId(id) {
        return usuarios.find(function (u) { return String(u.id) === String(id); });
    }

    table.querySelectorAll("[data-action='inativar-usuario']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const usuario = porId(button.getAttribute("data-usuario-id"));
            if (!usuario) return;

            const tipo = (TIPO_LABEL[usuario.tipo] || "usuário").toLowerCase();
            const mensagem = "Inativar o " + tipo + " " + usuario.nome + " (" + usuario.email + ")?\n\n"
                + "A conta deixa de fazer login. "
                + (AVISO_INATIVACAO[usuario.tipo] || "")
                + "\n\nO histórico de sessões, as avaliações e as mensagens do chat ficam guardados, "
                + "e a conta pode ser reativada depois.";
            if (!confirm(mensagem)) return;

            try {
                await inativarUsuario(usuario.id);
                await loadAdminUsuarios();
                showFeedback("feedback", usuario.nome + " foi inativado(a).", "success");
            } catch (error) {
                showFeedback("feedback", error.message, "error");
            }
        });
    });

    table.querySelectorAll("[data-action='reativar-usuario']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const usuario = porId(button.getAttribute("data-usuario-id"));
            if (!usuario) return;

            if (!confirm("Reativar " + usuario.nome + "? A conta volta a fazer login."
                    + (usuario.tipo === "TUTOR" ? "\n\nOs horários cancelados não voltam: o tutor precisa abrir novos." : ""))) {
                return;
            }

            try {
                await reativarUsuario(usuario.id);
                await loadAdminUsuarios();
                showFeedback("feedback", usuario.nome + " foi reativado(a).", "success");
            } catch (error) {
                showFeedback("feedback", error.message, "error");
            }
        });
    });

    // So a pedido do proprio aluno ou tutor, feito pessoalmente na secretaria
    table.querySelectorAll("[data-action='anonimizar-usuario']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const usuario = porId(button.getAttribute("data-usuario-id"));
            if (!usuario) return;

            const confirmacao = prompt(
                "Apagar os dados pessoais de " + usuario.nome + " (" + usuario.email + ")?\n\n"
                + "Use apenas a pedido do próprio usuário, feito na secretaria.\n\n"
                + "O nome e o e-mail serão apagados e a conta não poderá ser reativada. "
                + "O histórico de sessões, as avaliações e as mensagens continuam, como \"Usuário removido\".\n\n"
                + "ESTA AÇÃO NÃO PODE SER DESFEITA. Digite APAGAR para confirmar:");
            if (confirmacao === null) return;
            if (confirmacao.trim().toUpperCase() !== "APAGAR") {
                showFeedback("feedback", "Confirmação incorreta. Nenhum dado foi apagado.", "error");
                return;
            }

            try {
                await anonimizarUsuario(usuario.id);
                await loadAdminUsuarios();
                showFeedback("feedback", "Os dados pessoais de " + usuario.nome + " foram apagados.", "success");
            } catch (error) {
                showFeedback("feedback", error.message, "error");
            }
        });
    });

    table.querySelectorAll("[data-action='edit-usuario']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const usuario = porId(button.getAttribute("data-usuario-id"));
            if (!usuario) return;

            const novoNome = prompt("Novo nome:", usuario.nome);
            if (!novoNome) return;

            try {
                // Envia so o nome: a senha nunca muda por esta tela
                await putJSON("/usuario/" + usuario.id, { nome: novoNome });
                await loadAdminUsuarios();
            } catch (error) {
                showFeedback("feedback", error.message, "error");
            }
        });
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
