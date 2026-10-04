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

function statusBadge(status) {
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

    return "<span class=\"badge" + modifier + "\">" + escapeHTML(raw) + "</span>";
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

function computeTutorStats(tutorId, avaliacoes) {
    const list = (avaliacoes || []).filter(function (a) {
        return a.tutor && String(a.tutor.id) === String(tutorId);
    });

    if (!list.length) {
        return { average: 0, count: 0 };
    }

    const sum = list.reduce(function (acc, a) { return acc + (a.nota || 0); }, 0);
    return { average: sum / list.length, count: list.length };
}

function computeAverageRating(avaliacoes) {
    if (!avaliacoes || !avaliacoes.length) return "0.0";
    const sum = avaliacoes.reduce(function (acc, a) { return acc + (a.nota || 0); }, 0);
    return (sum / avaliacoes.length).toFixed(1);
}

function countByStatus(agendamentos, predicate) {
    return (agendamentos || []).filter(function(a) { return predicate(a.status); }).length;
}

function filterTutores(tutores, query, avaliacoes) {
    if (!query) return tutores || [];

    return (tutores || []).filter(function (tutor) {
        const name = formatUsuario(tutor.usuario).toLowerCase();
        const disciplina = String(tutor.disciplina || "").toLowerCase();
        return name.includes(query) || disciplina.includes(query);
    });
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
            + "<td>" + escapeHTML(agendamento.tutor && agendamento.tutor.disciplina) + "</td>"
            + "<td>" + escapeHTML(getTutorName(agendamento)) + "</td>"
            + "<td>" + escapeHTML(getEstudanteName(agendamento)) + "</td>"
            + "<td>" + escapeHTML(agendamento.data || "--") + "</td>"
            + "<td>" + escapeHTML(agendamento.hora ? agendamento.hora.substring(0, 5) : "--") + "</td>"
            + "<td>" + statusBadge(agendamento.status) + "</td>";

        if (config.showActions) {
            rows += "<td><div class=\"item-actions\">"
                + "<button class=\"btn outline-danger sm\" type=\"button\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\" data-action=\"reject-agendamento\" title=\"Recusar\">" + icon("x") + "</button>"
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
        const disciplina = agendamento.tutor ? agendamento.tutor.disciplina : "";
        const dataHora = (agendamento.data || "") + (agendamento.hora ? " às " + agendamento.hora.substring(0, 5) : "");
        const meta = disciplina + (dataHora ? " · " + dataHora : " · Aguardando confirmação");

        return "<div class=\"request-card\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(name)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(name) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(meta) + "</p>"
            + "</div>"
            + "<div class=\"item-actions\">"
            + "<button class=\"btn outline-danger\" type=\"button\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\" data-action=\"reject-agendamento\" title=\"Recusar\">" + icon("x") + "</button>"
            + "<button class=\"btn\" type=\"button\" data-agendamento-id=\"" + escapeHTML(agendamento.id) + "\" data-action=\"confirm-agendamento\">" + icon("check") + " Confirmar</button>"
            + "</div></div>";
    }).join("");
}

function renderTutorCards(id, tutores, avaliacoes) {
    const target = document.getElementById(id);
    if (!target) return;

    if (!tutores || !tutores.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhum tutor encontrado.</div>";
        return;
    }

    target.innerHTML = tutores.map(function (tutor, index) {
        const name = formatUsuario(tutor.usuario);
        const stats = computeTutorStats(tutor.id, avaliacoes);
        const rating = stats.count ? stats.average : 4.5 + (index % 5) * 0.1;
        const reviewCount = stats.count || 0;

        return "<div class=\"tutor-card\" data-tutor-id=\"" + escapeHTML(tutor.id) + "\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(name)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(name) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(tutor.disciplina) + "</p>"
            + renderStars(rating, reviewCount)
            + "<span class=\"availability-pill\">Disponível: horários a combinar</span>"
            + "</div>"
            + "<button class=\"btn\" type=\"button\" data-action=\"solicitar-tutoria\" data-tutor-id=\"" + escapeHTML(tutor.id) + "\">Solicitar Tutoria</button>"
            + "</div>";
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

    target.innerHTML = slice.map(function (avaliacao, index) {
        const name = getEstudanteName({ estudante: avaliacao.estudante });
        const nota = avaliacao.nota || 5;
        const comment = "Avaliação registrada em " + (avaliacao.dataAvaliacao || "") + " — nota " + nota + "/5.";

        return "<div class=\"review-card\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(name)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(name) + "</p>"
            + renderStars(nota, null)
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
            + "<p class=\"item-meta\">" + escapeHTML(tutor.disciplina) + "</p>"
            + "</div><span class=\"badge\">#" + escapeHTML(tutor.id) + "</span></div>";
    });
}

function renderEstudantesList(id, estudantes) {
    renderList(id, estudantes, function (estudante) {
        return "<div class=\"list-card\"><div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(formatUsuario(estudante.usuario)) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(estudante.diaDaSemana) + "</p>"
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

function bindSolicitarTutoriaButtons() {
    document.querySelectorAll("[data-action='solicitar-tutoria']").forEach(function (button) {
        button.addEventListener("click", async function () {
            const tutorId = button.getAttribute("data-tutor-id");
            const estudanteId = localStorage.getItem("estudanteId");

            if (!estudanteId) {
                alert("Perfil de estudante não encontrado. Faça login novamente.");
                return;
            }

            button.disabled = true;
            button.textContent = "Solicitando...";

            try {
                const sessoes = await fetchSessoesDisponiveisPorTutor(tutorId);

                if (!sessoes || !sessoes.length) {
                    button.textContent = "Sem sessões disponíveis";
                    button.classList.add("secondary");
                    return;
                }

                await solicitarAgendamento(sessoes[0].id, Number(estudanteId));

                button.textContent = "Aguardando aprovação";
                button.classList.add("secondary");
            } catch (err) {
                button.disabled = false;
                button.textContent = "Solicitar Tutoria";
                alert(err.message || "Erro ao solicitar tutoria.");
            }
        });
    });
}

function getQueryParam(name) {
    return new URLSearchParams(window.location.search).get(name);
}

// Cards de agendamento do ponto de vista do estudante
// Mostra: avatar do tutor, nome, disciplina, data/hora, status badge
function renderEstudanteAgendamentoCards(id, agendamentos, options) {
    const target = document.getElementById(id);
    if (!target) return;

    const list = agendamentos || [];
    const limit = options && options.limit ? options.limit : list.length;
    const slice = list.slice(0, limit);

    if (!slice.length) {
        target.innerHTML = "<div class=\"empty-state\">Nenhum agendamento encontrado.</div>";
        return;
    }

    target.innerHTML = slice.map(function (agendamento, index) {
        const tutorName = getTutorName(agendamento);
        const disciplina = agendamento.tutor ? (agendamento.tutor.disciplina || "") : "";
        const dataHora = (agendamento.data || "")
            + (agendamento.hora ? " às " + agendamento.hora.substring(0, 5) : "");
        const meta = (disciplina ? disciplina + (dataHora ? " · " + dataHora : "") : dataHora) || "Aguardando confirmação";

        return "<div class=\"request-card\">"
            + "<div class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(tutorName)) + "</div>"
            + "<div class=\"item-info\">"
            + "<p class=\"item-name\">" + escapeHTML(tutorName) + "</p>"
            + "<p class=\"item-meta\">" + escapeHTML(meta) + "</p>"
            + "</div>"
            + "<div class=\"item-actions\">"
            + statusBadge(agendamento.status)
            + "</div></div>";
    }).join("");
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
        const disciplina = sessao.tutor ? (sessao.tutor.disciplina || "") : "";
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
