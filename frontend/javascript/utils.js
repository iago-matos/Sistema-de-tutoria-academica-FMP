function escapeHTML(value) {
    return String(value ?? "--")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function setText(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}

function hideLoader() {
    const loading = document.getElementById("loading");
    if (loading) {
        loading.style.display = "none";
    }
}

function showLoader(text) {
    const loading = document.getElementById("loading");
    if (loading) {
        loading.style.display = "block";
        loading.className = "loading-state";
        loading.textContent = text || "Carregando dados...";
    }
}

function showError(message) {
    const loading = document.getElementById("loading");
    if (loading) {
        loading.style.display = "block";
        loading.className = "error-state";
        loading.textContent = message;
    }
}

function showFeedback(id, message, type) {
    const element = document.getElementById(id);
    if (!element) {
        return;
    }

    element.style.display = "block";
    element.className = type === "error" ? "error-state" : type === "success" ? "loading-state" : "integration-note";
    element.textContent = message;
}

function formatUsuario(usuario) {
    if (!usuario) {
        return "--";
    }

    return usuario.nome || usuario.email || ("Usuario #" + usuario.id);
}

function getTutorName(item) {
    return formatUsuario(item && item.tutor ? item.tutor.usuario : null);
}

function getEstudanteName(item) {
    return formatUsuario(item && item.estudante ? item.estudante.usuario : null);
}

function getInitials(name) {
    if (!name) return "?";
    const parts = String(name).trim().split(/\s+/);
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

const AVATAR_COLORS = ["purple", "blue", "teal", "orange"];

function avatarColorClass(index) {
    return AVATAR_COLORS[index % AVATAR_COLORS.length];
}

function normalizeStatus(status) {
    return String(status || "PENDENTE").toUpperCase();
}

function isPendingStatus(status) {
    const s = normalizeStatus(status);
    // PENDENTE = estudante solicitou, aguarda confirmação do tutor
    return s === "PENDENTE";
}

function isConfirmedStatus(status) {
    const s = normalizeStatus(status);
    return s === "CONFIRMADO" || s === "REALIZADO";
}

function isDisponivel(status) {
    return normalizeStatus(status) === "DISPONIVEL";
}

function isCancelledStatus(status) {
    const s = normalizeStatus(status);
    return s === "CANCELADO";
}

// Como o estudante le o status da propria solicitacao
const STATUS_ESTUDANTE = {
    PENDENTE: "Aguardando aprovação",
    CONFIRMADO: "Confirmada",
    REALIZADO: "Realizada",
    CANCELADO: "Cancelada"
};

// label e opcional: sem ele, o badge mostra o status cru (PENDENTE...)
function statusBadge(status, label) {
    const raw = String(status || "PENDENTE");
    const s = raw.toUpperCase();
    let modifier = "";

    if (s === "CONFIRMADO" || s === "REALIZADO") {
        modifier = " success";
    } else if (s === "CANCELADO") {
        modifier = " danger";
    } else if (s === "PENDENTE") {
        modifier = " warning";
    } else if (s === "DISPONIVEL") {
        modifier = ""; // azul padrão
    }

    return "<span class=\"badge" + modifier + "\">" + escapeHTML(label || raw) + "</span>";
}

// Status da conta do usuario (diferente do status do agendamento)
function usuarioStatusBadge(status) {
    const s = String(status || "").toUpperCase();
    const modifier = s === "ATIVO" ? " success"
        : s === "PENDENTE" ? " warning"
        : s === "RECUSADO" ? " danger"
        : s === "INATIVO" || s === "ANONIMIZADO" ? " neutral"
        : "";
    return "<span class=\"badge" + modifier + "\">" + escapeHTML(s || "--") + "</span>";
}

function renderStars(rating, reviewCount) {
    const fullStars = Math.round(Number(rating) || 0);
    let starsHtml = "";

    for (let i = 1; i <= 5; i++) {
        starsHtml += i <= fullStars ? "★" : "☆";
    }

    const countLabel = reviewCount != null
        ? "<span class=\"review-count\">(" + escapeHTML(reviewCount) + " avaliações)</span>"
        : "";

    return "<span class=\"stars\">" + starsHtml
        + "<span class=\"rating-num\">" + escapeHTML(Number(rating || 0).toFixed(1)) + "</span>"
        + countLabel + "</span>";
}

function computeAverageRating(avaliacoes) {
    if (!avaliacoes || !avaliacoes.length) return "0.0";
    const sum = avaliacoes.reduce(function (acc, a) { return acc + (a.nota || 0); }, 0);
    return (sum / avaliacoes.length).toFixed(1);
}

function countByStatus(agendamentos, predicate) {
    return (agendamentos || []).filter(function(a) { return predicate(a.status); }).length;
}

function renderAgendamentosTable(tableId, agendamentos, options) {
    const table = document.getElementById(tableId);
    const config = options || {};

    if (!table) {
        return;
    }

    if (!Array.isArray(agendamentos) || agendamentos.length === 0) {
        table.innerHTML = "<tbody><tr><td colspan=\"7\"><div class=\"empty-state\">Nenhum agendamento encontrado.</div></td></tr></tbody>";
        return;
    }

    let rows = "";

    agendamentos.forEach(function (agendamento) {
        rows += "<tr>"
            + "<td>#" + escapeHTML(agendamento.id) + "</td>"
            + "<td>" + escapeHTML(disciplinaDaSessao(agendamento)) + "</td>"
            + "<td>" + escapeHTML(getTutorName(agendamento)) + "</td>"
            + "<td>" + escapeHTML(getEstudanteName(agendamento)) + "</td>"
            + "<td>" + escapeHTML(agendamento.data || "--") + "</td>"
            + "<td>" + escapeHTML(agendamento.hora ? agendamento.hora.substring(0, 5) : "--") + "</td>"
            + "<td>" + statusBadge(agendamento.status) + "</td>";

        if (config.showActions) {
            rows += "<td><div class=\"item-actions\">"
                + "<button class=\"btn outline-danger sm\" type=\"button\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\" data-action=\"reject-agendamento\" title=\"Recusar\" aria-label=\"Recusar\">" + icon("x") + "</button>"
                + "<button class=\"btn sm\" type=\"button\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\" data-action=\"confirm-agendamento\">" + icon("check") + " Confirmar</button>"
                + "</div></td>";
        }

        rows += "</tr>";
    });

    let header = "<thead><tr>"
        + "<th>ID</th><th>Disciplina</th><th>Tutor</th><th>Estudante</th><th>Data</th><th>Hora</th><th>Status</th>";

    if (config.showActions) {
        header += "<th>Ações</th>";
    }

    header += "</tr></thead>";

    table.innerHTML = header + "<tbody>" + rows + "</tbody>";
}

function renderPendingRequests(id, agendamentos, options) {
    const target = document.getElementById(id);
    if (!target) return;

    const pending = (agendamentos || []).filter(function (a) {
        return isPendingStatus(a.status);
    });

    const limit = options && options.limit ? options.limit : pending.length;
    const slice = pending.slice(0, limit);

    if (!slice.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhuma solicitação pendente.</div>";
        return;
    }

    target.innerHTML = slice.map(function (agendamento, index) {
        const name = getEstudanteName(agendamento);
        const disciplina = disciplinaDaSessao(agendamento);
        const dataHora = (agendamento.data || "") + (agendamento.hora ? " às " + agendamento.hora.substring(0, 5) : "");
        const meta = disciplina + (dataHora ? " · " + dataHora : " · Aguardando confirmação");

        return "<div class=\"request-card\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(name)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(name) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(meta) + "</p>"
            + "</div>"
            + "<div class=\"item-actions\">"
            + "<button class=\"btn outline-danger\" type=\"button\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\" data-action=\"reject-agendamento\" title=\"Recusar\" aria-label=\"Recusar\">" + icon("x") + "</button>"
            + "<button class=\"btn\" type=\"button\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\" data-action=\"confirm-agendamento\">" + icon("check") + " Confirmar</button>"
            + "</div></div>";
    }).join("");
}

function renderReviewCards(id, avaliacoes, options) {
    const target = document.getElementById(id);
    if (!target) return;

    const limit = options && options.limit ? options.limit : (avaliacoes || []).length;
    const slice = (avaliacoes || []).slice(0, limit);

    if (!slice.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhuma avaliação encontrada.</div>";
        return;
    }

    target.innerHTML = reviewCardsHTML(slice, options);
}

// options.pessoa = "tutor": na tela do estudante, mostra quem foi avaliado
// options.disciplina = true: mostra a disciplina mesmo listando estudantes
function reviewCardsHTML(avaliacoes, options) {
    const mostrarTutor = options && options.pessoa === "tutor";
    const mostrarDisciplina = mostrarTutor || Boolean(options && options.disciplina);

    return avaliacoes.map(function (avaliacao, index) {
        const name = mostrarTutor
            ? getTutorName({ tutor: avaliacao.tutor })
            : getEstudanteName({ estudante: avaliacao.estudante });
        const nota = avaliacao.nota || 5;
        const quando = avaliacao.dataAvaliacao
            ? "Avaliado em " + avaliacao.dataAvaliacao.split("-").reverse().join("/")
            : "";
        const disciplina = !mostrarDisciplina ? ""
            : avaliacao.disciplina && avaliacao.disciplina.nome ? avaliacao.disciplina.nome
            : disciplinasDoTutor(avaliacao.tutor);
        const tutoria = avaliacao.dataTutoria
            ? "Tutoria de " + avaliacao.dataTutoria.split("-").reverse().slice(0, 2).join("/")
            : "";
        const comment = [disciplina, tutoria, quando].filter(Boolean).join(" · ");

        return "<div class=\"review-card\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(name)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(name) + "</p>"
            + renderStars(nota, null)
            + (avaliacao.comentario
                ? "<p class=\"review-comentario\">" + escapeHTML(avaliacao.comentario) + "</p>"
                : "")
            + "<p class=\"review-text\">" + escapeHTML(comment) + "</p>"
            + "</div></div>";
    }).join("");
}

function renderList(id, items, mapItem) {
    const target = document.getElementById(id);

    if (!target) {
        return;
    }

    if (!Array.isArray(items) || items.length === 0) {
        target.innerHTML = "<div class=\"empty-state\">Nenhum registro encontrado.</div>";
        return;
    }

    target.innerHTML = items.map(mapItem).join("");
}

function renderTutoresList(id, tutores) {
    renderList(id, tutores, function (tutor) {
        return "<div class=\"list-card\"><div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(formatUsuario(tutor.usuario)) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(disciplinasDoTutor(tutor)) + "</p>"
            + "</div><span class=\"badge\">#" + escapeHTML(tutor.id) + "</span></div>";
    });
}

function renderEstudantesList(id, estudantes) {
    renderList(id, estudantes, function (estudante) {
        return "<div class=\"list-card\"><div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(formatUsuario(estudante.usuario)) + "</p>"
            + "</div><span class=\"badge\">#" + escapeHTML(estudante.id) + "</span></div>";
    });
}

function renderUsuariosList(id, usuarios) {
    renderList(id, usuarios, function (usuario) {
        return "<div class=\"list-card\"><div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(usuario.nome) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(usuario.email) + "</p>"
            + "</div><span class=\"badge\">#" + escapeHTML(usuario.id) + "</span></div>";
    });
}

function renderAvaliacoesList(id, avaliacoes) {
    renderReviewCards(id, avaliacoes);
}

function fillSelect(id, items, labelFn, valueFn) {
    const select = document.getElementById(id);
    if (!select) {
        return;
    }

    select.innerHTML = items.map(function (item) {
        return "<option value=\"" + escapeHTML(valueFn(item)) + "\">" + escapeHTML(labelFn(item)) + "</option>";
    }).join("");
}

function bindAgendamentoActions(reloadFn) {
    document.querySelectorAll("[data-action='confirm-agendamento']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const id = button.getAttribute("data-agendamento-id");
            try {
                await updateAgendamento(id, { status: "CONFIRMADO" });
                if (reloadFn) await reloadFn();
            } catch (error) {
                alert(error.message);
            }
        });
    });

    document.querySelectorAll("[data-action='reject-agendamento']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const id = button.getAttribute("data-agendamento-id");
            try {
                await updateAgendamento(id, { status: "CANCELADO" });
                if (reloadFn) await reloadFn();
            } catch (error) {
                alert(error.message);
            }
        });
    });
}

function getQueryParam(name) {
    return new URLSearchParams(window.location.search).get(name);
}

// "Seg, 10/11/2026 · 14:00 – 15:00"
function formatarHorario(agendamento) {
    if (!agendamento || !agendamento.data) return "";
    const dia = new Date(agendamento.data + "T00:00").toLocaleDateString("pt-BR", {
        weekday: "short", day: "2-digit", month: "2-digit", year: "numeric"
    });
    const inicio = agendamento.hora ? agendamento.hora.substring(0, 5) : "--";
    const fim = agendamento.horaFim ? agendamento.horaFim.substring(0, 5) : "";
    return dia.charAt(0).toUpperCase() + dia.slice(1) + " · " + inicio + (fim ? " – " + fim : "");
}

// "2026-11-07" no fuso do navegador (toISOString usaria UTC)
function hojeISO() {
    const d = new Date();
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
}

// O estudante so cancela ate a vespera: no dia da tutoria, nao.
// (O backend aplica a mesma regra; aqui e so para esconder o botao.)
function antesDoDiaDaSessao(data) {
    return Boolean(data) && data > hojeISO();
}

function podeCancelarComoEstudante(agendamento) {
    const status = normalizeStatus(agendamento.status);
    return antesDoDiaDaSessao(agendamento.data) && (status === "PENDENTE" || status === "CONFIRMADO");
}

const BOTAO_CANCELAR_AGENDAMENTO = "Cancelar agendamento";

// Cards de agendamento do ponto de vista do estudante
// Mostra: avatar do tutor, nome, disciplina, data/hora, status e, se
// options.onCancelar for informado, o botao para cancelar a solicitacao
function renderEstudanteAgendamentoCards(id, agendamentos, options) {
    const target = document.getElementById(id);
    if (!target) return;

    const config = options || {};
    const list = agendamentos || [];
    const limit = config.limit ? config.limit : list.length;
    const slice = list.slice(0, limit);

    if (!slice.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhum agendamento encontrado.</div>";
        return;
    }

    target.innerHTML = slice.map(function (agendamento, index) {
        const tutorName = getTutorName(agendamento);
        const disciplina = disciplinaDaSessao(agendamento);
        const horario = formatarHorario(agendamento);
        const modalidade = MODALIDADE_LABEL[agendamento.modalidade] || "";
        const meta = [disciplina, horario, modalidade].filter(Boolean).join(" · ") || "Aguardando confirmação";
        const link = linkSeguro(agendamento.linkReuniao);
        const entrar = link
            ? "<a class=\"btn sm\" href=\"" + escapeHTML(link) + "\" target=\"_blank\" rel=\"noopener noreferrer\">Entrar na reunião</a>"
            : "";
        const aviso = agendamento.modalidade === "ONLINE" && normalizeStatus(agendamento.status) === "PENDENTE"
            ? "<p class=\"item-meta\" style=\"margin-top:2px;\">O link da reunião aparece aqui quando o tutor confirmar.</p>"
            : "";
        const cancelar = config.onCancelar && podeCancelarComoEstudante(agendamento)
            ? "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"cancelar-solicitacao\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\">" + BOTAO_CANCELAR_AGENDAMENTO + "</button>"
            : "";
        const status = normalizeStatus(agendamento.status);

        return "<div class=\"request-card\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(tutorName)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(tutorName) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(meta) + "</p>"
            + aviso
            + "</div>"
            + "<div class=\"item-actions\">"
            + statusBadge(agendamento.status, STATUS_ESTUDANTE[status])
            + entrar
            + cancelar
            + "</div></div>";
    }).join("");

    target.querySelectorAll("[data-action='cancelar-solicitacao']").forEach(function (btn) {
        btn.addEventListener("click", async function () {
            btn.disabled = true;
            try {
                await config.onCancelar(btn.getAttribute("data-agendamento-id"));
            } catch (err) {
                alert(err.message);
            } finally {
                btn.disabled = false;
            }
        });
    });
}

// ── Disciplinas ──────────────────────────────────────────────────────────

// "Banco de Dados, Algoritmos": disciplinas ativas de um tutor
function disciplinasDoTutor(tutor) {
    return tutor && Array.isArray(tutor.disciplinas)
        ? tutor.disciplinas.map(function (d) { return d.nome; }).join(", ")
        : "";
}

// Disciplina de uma sessao: a escolhida pelo estudante ao solicitar ou, num
// horario ainda livre, as disciplinas do tutor
function disciplinaDaSessao(agendamento) {
    if (!agendamento) return "";
    if (agendamento.disciplina && agendamento.disciplina.nome) return agendamento.disciplina.nome;
    return disciplinasDoTutor(agendamento.tutor);
}

// ── Guia "Tutores" do estudante: disciplinas ─────────────────────────────

// "Cálculo I", " cálculo i " e "CÁLCULO I" sao a mesma disciplina
function normalizarDisciplina(nome) {
    return String(nome || "").trim().toLowerCase();
}

// O tutor atende a disciplina (entre as ativas)?
function tutorAtende(tutor, nomeDisciplina) {
    const chave = normalizarDisciplina(nomeDisciplina);
    return Boolean(tutor && Array.isArray(tutor.disciplinas) && tutor.disciplinas.some(function (d) {
        return normalizarDisciplina(d.nome) === chave;
    }));
}

// Junta tutores e horarios livres por disciplina, para os cards da lista.
// Um tutor com varias disciplinas conta em cada uma, e o horario livre dele
// tambem: o estudante escolhe a disciplina ao solicitar.
function agruparPorDisciplina(tutores, horarios) {
    const grupos = new Map();
    const disciplinasPorTutor = new Map();

    (tutores || []).forEach(function (tutor) {
        const chaves = [];
        (tutor.disciplinas || []).forEach(function (disciplina) {
            const chave = normalizarDisciplina(disciplina.nome);
            if (!chave) return;
            if (!grupos.has(chave)) {
                grupos.set(chave, { id: disciplina.id, nome: disciplina.nome.trim(), totalTutores: 0, totalHorarios: 0, nomesTutores: [] });
            }
            const grupo = grupos.get(chave);
            grupo.totalTutores++;
            grupo.nomesTutores.push(formatUsuario(tutor.usuario));
            chaves.push(chave);
        });
        disciplinasPorTutor.set(tutor.id, chaves);
    });

    (horarios || []).forEach(function (horario) {
        (disciplinasPorTutor.get(horario.tutor && horario.tutor.id) || []).forEach(function (chave) {
            grupos.get(chave).totalHorarios++;
        });
    });

    return Array.from(grupos.values()).sort(function (a, b) {
        return a.nome.localeCompare(b.nome, "pt-BR");
    });
}

function plural(n, singular, pluralForm) {
    return n + " " + (n === 1 ? singular : pluralForm);
}

function renderDisciplinaCards(id, disciplinas) {
    const target = document.getElementById(id);
    if (!target) return;

    if (!disciplinas.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhuma disciplina encontrada.</div>";
        return;
    }

    target.innerHTML = "<div class=\"disciplina-grid\">" + disciplinas.map(function (d, index) {
        const horarios = d.totalHorarios
            ? "<strong>" + escapeHTML(plural(d.totalHorarios, "horário livre", "horários livres")) + "</strong>"
            : "Sem horários livres no momento";
        return "<a class=\"disciplina-card\" href=\"?disciplina=" + encodeURIComponent(d.nome) + "\">"
            + "<div class=\"stat-icon " + ["blue", "purple", "green", "orange"][index % 4] + "\">" + icon("book") + "</div>"
            + "<div>"
            + "<div class=\"disciplina-nome\">" + escapeHTML(d.nome) + "</div>"
            + "<div class=\"disciplina-meta\">" + escapeHTML(plural(d.totalTutores, "tutor", "tutores")) + " · " + horarios + "</div>"
            + "</div></a>";
    }).join("") + "</div>";
}

// Com link do tutor, o estudante escolhe presencial ou online; sem link, so presencial
function botoesSolicitarHorario(onlineDisponivel) {
    if (!onlineDisponivel) {
        return "<button class=\"btn sm\" type=\"button\" data-action=\"solicitar-horario\" data-modalidade=\"PRESENCIAL\">Solicitar tutoria</button>";
    }
    return "<button class=\"btn secondary sm\" type=\"button\" data-action=\"solicitar-horario\" data-modalidade=\"PRESENCIAL\">Solicitar presencial</button>"
        + "<button class=\"btn sm\" type=\"button\" data-action=\"solicitar-horario\" data-modalidade=\"ONLINE\">Solicitar online</button>";
}

const MODALIDADE_LABEL = { PRESENCIAL: "Presencial (na instituição)", ONLINE: "Online" };

// So abre links https: protege contra "javascript:" mesmo se o backend falhar
function linkSeguro(url) {
    return typeof url === "string" && /^https:\/\//i.test(url) ? url : null;
}

// Link da videoconferencia no popup da disciplina (abre em nova aba)
function linkDoHorario(url) {
    const link = linkSeguro(url);
    if (!link) return "";
    return "<div class=\"slot-link\">Link: <a href=\"" + escapeHTML(link) + "\" target=\"_blank\" rel=\"noopener noreferrer\">"
        + escapeHTML(link) + "</a></div>";
}

// Depois de solicitar: "Aguardando aprovação" e, ate a vespera, o botao de cancelar
function acoesHorarioSolicitado(data, podeCancelar) {
    return statusBadge("PENDENTE", STATUS_ESTUDANTE.PENDENTE)
        + (podeCancelar && antesDoDiaDaSessao(data)
            ? "<button class=\"btn outline-danger sm\" type=\"button\" data-action=\"cancelar-solicitacao\">" + BOTAO_CANCELAR_AGENDAMENTO + "</button>"
            : "");
}

// Tutores de uma disciplina (conteudo do popup), cada um com avaliacao e
// horarios para solicitar. onCancelar(horarioId) e opcional; se devolver
// false, nada muda na tela. Erros aparecem em feedbackId.
function renderDetalheDisciplina(id, tutores, horarios, onSolicitar, onCancelar, feedbackId) {
    const target = document.getElementById(id);
    if (!target) return;

    if (!tutores.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhum tutor ativo nesta disciplina.</div>";
        return;
    }

    const horariosDoTutor = function (tutorId) {
        return horarios.filter(function (h) { return h.tutor && h.tutor.id === tutorId; });
    };

    // Quem tem horario livre aparece primeiro; depois, a melhor avaliacao
    const ordenados = tutores.slice().sort(function (a, b) {
        const livresA = horariosDoTutor(a.id).length > 0 ? 1 : 0;
        const livresB = horariosDoTutor(b.id).length > 0 ? 1 : 0;
        return (livresB - livresA) || ((b.mediaAvaliacoes || 0) - (a.mediaAvaliacoes || 0));
    });

    target.innerHTML = ordenados.map(function (tutor, index) {
        const nome = formatUsuario(tutor.usuario);
        const avaliacao = tutor.totalAvaliacoes
            ? renderStars(tutor.mediaAvaliacoes, tutor.totalAvaliacoes)
            : "<span class=\"stars\"><span class=\"review-count\">Sem avaliações ainda</span></span>";
        const livres = horariosDoTutor(tutor.id);

        const slots = livres.length
            ? "<div class=\"slot-list\">" + livres.map(function (h) {
                return "<div class=\"slot-item\">"
                    + "<div class=\"slot-when\">"
                    + "<div class=\"slot-date\">" + escapeHTML(formatarHorario(h)) + " <span class=\"badge neutral\">1h</span></div>"
                    + "<div class=\"slot-modalidade\">" + (h.onlineDisponivel
                        ? "Presencial na instituição ou online"
                        : "Somente presencial, na instituição") + "</div>"
                    + linkDoHorario(h.linkReuniao)
                    + (h.observacoes ? "<div class=\"slot-obs\">" + escapeHTML(h.observacoes) + "</div>" : "")
                    + "</div>"
                    + "<div class=\"item-actions\" data-horario-id=\"" + escapeHTML(h.id) + "\" data-horario-data=\"" + escapeHTML(h.data) + "\" data-online=\"" + (h.onlineDisponivel ? "true" : "false") + "\">"
                    + botoesSolicitarHorario(h.onlineDisponivel)
                    + "</div></div>";
            }).join("") + "</div>"
            : "<p class=\"slot-empty\">Nenhum horário livre no momento.</p>";

        return "<div class=\"tutor-detalhe\">"
            + "<div class=\"tutor-detalhe-header\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(nome)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(nome) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(disciplinasDoTutor(tutor)) + "</p>"
            + avaliacao
            + "</div></div>"
            + slots
            + "</div>";
    }).join("");

    // Delegacao: os botoes de cada horario sao trocados depois de cada acao
    target.onclick = async function (event) {
        const btn = event.target.closest("button[data-action]");
        const acoes = btn && btn.closest("[data-horario-id]");
        if (!acoes || btn.disabled) return;

        const horarioId = Number(acoes.getAttribute("data-horario-id"));
        const data = acoes.getAttribute("data-horario-data");
        const acao = btn.getAttribute("data-action");
        const textoOriginal = btn.textContent;
        btn.disabled = true;

        try {
            if (acao === "solicitar-horario") {
                btn.textContent = "Enviando...";
                await onSolicitar(horarioId, btn.getAttribute("data-modalidade") || "PRESENCIAL");
                acoes.innerHTML = acoesHorarioSolicitado(data, Boolean(onCancelar));
            } else if (acao === "cancelar-solicitacao") {
                if (await onCancelar(horarioId) === false) {
                    btn.disabled = false;
                    return;
                }
                acoes.innerHTML = botoesSolicitarHorario(acoes.getAttribute("data-online") === "true");
            }
        } catch (err) {
            btn.disabled = false;
            btn.textContent = textoOriginal;
            showFeedback(feedbackId || "feedback", err.message, "error");
        }
    };
}

// Cards de sessões disponíveis para o estudante solicitar
function renderSessoesDisponiveis(id, sessoes, onSolicitar) {
    const target = document.getElementById(id);
    if (!target) return;

    if (!sessoes || !sessoes.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhuma sessão disponível no momento.</div>";
        return;
    }

    target.innerHTML = sessoes.map(function (sessao, index) {
        const tutorName = getTutorName(sessao);
        const disciplina = disciplinaDaSessao(sessao);
        const data = sessao.data ? sessao.data.split("-").reverse().join("/") : "--";
        const hora = sessao.hora ? sessao.hora.substring(0, 5) : "--";
        const obs = sessao.observacoes ? sessao.observacoes : "";

        return "<div class=\"request-card\" style=\"align-items:flex-start;gap:16px;\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\" style=\"margin-top:2px;flex-shrink:0;\">"
            + escapeHTML(getInitials(tutorName)) + "</div>"
            + "<div class=\"item-info\" style=\"flex:1;min-width:0;\">"
            + "<p class=\"item-name\">" + escapeHTML(tutorName) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(disciplina) + "</p>"
            + "<p class=\"item-meta\" style=\"margin-top:4px;\">"
            + "<span style=\"color:var(--dark);font-weight:500;\">" + escapeHTML(data) + " às " + escapeHTML(hora) + "</span>"
            + "</p>"
            + (obs ? "<p class=\"item-meta\" style=\"margin-top:4px;font-style:italic;\">" + escapeHTML(obs) + "</p>" : "")
            + "</div>"
            + "<button class=\"btn\" type=\"button\" data-sessao-id=\"" + escapeHTML(sessao.id) + "\" data-action=\"solicitar-sessao\""
            + " style=\"flex-shrink:0;align-self:center;\">Solicitar</button>"
            + "</div>";
    }).join("");

    // Bind dos botões
    target.querySelectorAll("[data-action='solicitar-sessao']").forEach(function (btn) {
        btn.addEventListener("click", async function () {
            const sessaoId = btn.getAttribute("data-sessao-id");
            btn.disabled = true;
            btn.textContent = "Enviando...";
            try {
                await onSolicitar(Number(sessaoId));
                btn.textContent = "Solicitado ✓";
                btn.classList.add("secondary");
            } catch (err) {
                btn.disabled = false;
                btn.textContent = "Solicitar";
                showFeedback("feedback", err.message, "error");
            }
        });
    });
}
