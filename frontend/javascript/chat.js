// Tela "Mensagens" (estudante e tutor): lista de conversas a esquerda e a
// conversa aberta a direita. No celular, mostra uma coisa de cada vez.
//
// Mensagens novas chegam por consulta periodica (a cada 5 s, so com a aba
// visivel), pedindo ao backend apenas as mensagens depois da ultima recebida.

const CHAT_INTERVALO_MS = 5000;
const CHAT_MAX_CARACTERES = 2000;

const chat = {
    conversas: [],
    atual: null,       // resumo da conversa aberta
    ultimaId: 0,       // id da ultima mensagem recebida da conversa aberta
    ultimoDia: null,   // para os separadores "Hoje", "Ontem"...
    timer: null,
    ciclos: 0
};

async function loadMensagens() {
    const role = document.body.dataset.role;
    const user = await requireAuth(role === "tutor" ? "TUTOR" : "ESTUDANTE");
    if (!user) return;

    initAppShell({
        header: {
            mode: "title",
            title: "Mensagens",
            subtitle: role === "tutor"
                ? "Converse com os estudantes que solicitaram tutoria com você."
                : "Tire dúvidas com os tutores antes ou depois das tutorias."
        }
    });

    ligarEventosDoChat();

    // ?tutorId= (estudante) ou ?estudanteId= (tutor) abre a conversa com a pessoa;
    // se ainda nao existe, ela e criada. ?conversa= abre uma conversa existente.
    let abrirId = Number(getQueryParam("conversa")) || null;
    const tutorId = Number(getQueryParam("tutorId")) || null;
    const estudanteId = Number(getQueryParam("estudanteId")) || null;
    if (tutorId || estudanteId) {
        try {
            const conversa = await iniciarConversa(tutorId ? { tutorId: tutorId } : { estudanteId: estudanteId });
            abrirId = conversa.id;
            history.replaceState(null, "", "mensagens.html?conversa=" + conversa.id);
        } catch (error) {
            showFeedback("chatFeedback", error.message, "error");
        }
    }

    hideLoader();
    await carregarConversas();
    if (abrirId) {
        await abrirConversa(abrirId);
    }

    chat.timer = setInterval(cicloDoChat, CHAT_INTERVALO_MS);
}

function ligarEventosDoChat() {
    const form = document.getElementById("chatForm");
    const campo = document.getElementById("chatTexto");

    form.addEventListener("submit", function (event) {
        event.preventDefault();
        enviarDoCampo();
    });

    // Enter envia; Shift+Enter quebra a linha
    campo.addEventListener("keydown", function (event) {
        if (event.key === "Enter" && !event.shiftKey && !event.isComposing) {
            event.preventDefault();
            enviarDoCampo();
        }
    });

    campo.addEventListener("input", function () {
        ajustarAlturaDoCampo(campo);
        const restante = CHAT_MAX_CARACTERES - campo.value.length;
        const contador = document.getElementById("chatContador");
        contador.hidden = restante > 200;
        contador.textContent = restante + " caracteres restantes";
    });

    document.getElementById("chatVoltar").addEventListener("click", function () {
        fecharConversaNoCelular();
    });

    document.getElementById("chatLista").addEventListener("click", function (event) {
        const item = event.target.closest("[data-conversa-id]");
        if (item) abrirConversa(Number(item.getAttribute("data-conversa-id")));
    });

    // Ao voltar para a aba, busca o que chegou enquanto ela estava escondida
    document.addEventListener("visibilitychange", function () {
        if (!document.hidden) cicloDoChat();
    });
}

function ajustarAlturaDoCampo(campo) {
    campo.style.height = "auto";
    campo.style.height = Math.min(campo.scrollHeight, 160) + "px";
}

async function cicloDoChat() {
    if (document.hidden) return;
    chat.ciclos += 1;
    try {
        if (chat.atual) {
            await buscarMensagensNovas();
        }
        // A lista (nao lidas, ultima mensagem) e atualizada com menos frequencia
        if (chat.ciclos % 3 === 0 || !chat.atual) {
            await carregarConversas();
        }
    } catch (_) {
        // Falha de rede passageira: tenta de novo no proximo ciclo
    }
}

// ── Lista de conversas ──────────────────────────────────────────────────────

async function carregarConversas() {
    try {
        chat.conversas = await fetchConversas() || [];
    } catch (error) {
        showFeedback("chatFeedback", error.message, "error");
        return;
    }
    renderListaDeConversas();
}

function renderListaDeConversas() {
    const lista = document.getElementById("chatLista");

    if (!chat.conversas.length) {
        const dica = document.body.dataset.role === "tutor"
            ? "Para falar com um estudante, use o botão <strong>Mensagem</strong> em Solicitações ou nos detalhes de uma sessão da Agenda."
            : "Para falar com um tutor, abra a guia <a href=\"buscar-tutores.html\">Tutores</a>, escolha a disciplina e clique em <strong>Conversar</strong>.";
        lista.innerHTML = "<div class=\"chat-vazio\">" + icon("chat") + "<p>Nenhuma conversa ainda.</p><p class=\"muted\">" + dica + "</p></div>";
        return;
    }

    lista.innerHTML = chat.conversas.map(function (c, index) {
        const ativa = chat.atual && chat.atual.id === c.id ? " ativa" : "";
        const previa = c.ultimaMensagem
            ? (c.ultimaMensagemMinha ? "Você: " : "") + c.ultimaMensagem
            : "Nenhuma mensagem ainda";
        const naoLidas = c.naoLidas > 0
            ? "<span class=\"notif-badge\" aria-label=\"" + c.naoLidas + " não lidas\">" + c.naoLidas + "</span>"
            : "";
        return "<button type=\"button\" class=\"chat-item" + ativa + (c.naoLidas > 0 ? " nao-lida" : "") + "\" data-conversa-id=\"" + escapeHTML(c.id) + "\">"
            + "<span class=\"avatar " + avatarColorClass(index) + "\">" + escapeHTML(getInitials(c.interlocutorNome)) + "</span>"
            + "<span class=\"chat-item-info\">"
            + "<span class=\"chat-item-topo\"><strong>" + escapeHTML(c.interlocutorNome) + "</strong>"
            + "<span class=\"chat-item-hora\">" + escapeHTML(horaCurta(c.ultimaMensagemEm)) + "</span></span>"
            + "<span class=\"chat-item-previa\">" + escapeHTML(previa) + "</span>"
            + "<span class=\"chat-item-papel\">" + (c.interlocutorPapel === "TUTOR" ? "Tutor" : "Estudante")
            + (c.aberta ? "" : " · Encerrada") + "</span>"
            + "</span>"
            + naoLidas
            + "</button>";
    }).join("");
}

// ── Conversa aberta ────────────────────────────────────────────────────────

async function abrirConversa(conversaId) {
    let resumo = chat.conversas.find(function (c) { return c.id === conversaId; });
    try {
        if (!resumo) resumo = await fetchConversa(conversaId);
    } catch (error) {
        showFeedback("chatFeedback", error.message, "error");
        return;
    }

    chat.atual = resumo;
    chat.ultimaId = 0;
    chat.ultimoDia = null;

    document.getElementById("chatLayout").classList.add("mostrando-conversa");
    document.getElementById("chatVazio").hidden = true;
    document.getElementById("chatConversa").hidden = false;
    document.getElementById("chatNome").textContent = resumo.interlocutorNome;
    document.getElementById("chatPapel").textContent = resumo.interlocutorPapel === "TUTOR" ? "Tutor" : "Estudante";
    document.getElementById("chatAvatar").textContent = getInitials(resumo.interlocutorNome);
    document.getElementById("chatMensagens").innerHTML = "<div class=\"loading-state\">Carregando mensagens...</div>";
    mostrarCaixaDeTexto(resumo);
    renderListaDeConversas();

    try {
        const mensagens = await fetchMensagens(conversaId);
        if (!chat.atual || chat.atual.id !== conversaId) return; // trocou de conversa no meio
        document.getElementById("chatMensagens").innerHTML = "";
        adicionarMensagens(mensagens, true);
        // Abrir marca as mensagens recebidas como lidas
        resumo.naoLidas = 0;
        renderListaDeConversas();
        atualizarNotificacoes();
    } catch (error) {
        document.getElementById("chatMensagens").innerHTML = "<div class=\"error-state\">" + escapeHTML(error.message) + "</div>";
    }

    if (resumo.aberta && window.matchMedia("(min-width: 721px)").matches) {
        document.getElementById("chatTexto").focus();
    }
}

function mostrarCaixaDeTexto(resumo) {
    const form = document.getElementById("chatForm");
    const encerrada = document.getElementById("chatEncerrada");
    form.hidden = !resumo.aberta;
    encerrada.hidden = resumo.aberta;
    if (!resumo.aberta) {
        encerrada.textContent = "Esta conversa está encerrada: " + resumo.interlocutorNome
            + " não está mais ativo(a) no sistema. O histórico continua disponível para consulta.";
    }
}

function fecharConversaNoCelular() {
    document.getElementById("chatLayout").classList.remove("mostrando-conversa");
}

async function buscarMensagensNovas() {
    const conversaId = chat.atual.id;
    const novas = await fetchMensagens(conversaId, chat.ultimaId);
    if (!chat.atual || chat.atual.id !== conversaId || !novas.length) return;
    adicionarMensagens(novas, false);
    atualizarNotificacoes();
}

// Acrescenta mensagens ao fim da conversa, sem repetir as que ja estao na tela
function adicionarMensagens(mensagens, rolarSempre) {
    const area = document.getElementById("chatMensagens");
    const pertoDoFim = area.scrollHeight - area.scrollTop - area.clientHeight < 80;

    const novas = (mensagens || []).filter(function (m) { return m.id > chat.ultimaId; });
    if (!novas.length && !area.children.length) {
        area.innerHTML = "<div class=\"chat-inicio\">Nenhuma mensagem ainda. Diga olá! 👋</div>";
        return;
    }

    const inicio = area.querySelector(".chat-inicio");
    if (inicio && novas.length) inicio.remove();

    novas.forEach(function (m) {
        const dia = (m.enviadaEm || "").substring(0, 10);
        if (dia && dia !== chat.ultimoDia) {
            chat.ultimoDia = dia;
            const separador = document.createElement("div");
            separador.className = "chat-dia";
            separador.textContent = rotuloDoDia(dia);
            area.appendChild(separador);
        }

        const balao = document.createElement("div");
        balao.className = "chat-msg " + (m.minha ? "minha" : "dele");
        balao.innerHTML = "<p></p><span class=\"chat-msg-hora\"></span>";
        balao.querySelector("p").textContent = m.conteudo;
        balao.querySelector(".chat-msg-hora").textContent = horaCurta(m.enviadaEm, true);
        area.appendChild(balao);
        chat.ultimaId = Math.max(chat.ultimaId, m.id);
    });

    if (rolarSempre || pertoDoFim) {
        area.scrollTop = area.scrollHeight;
    }
}

async function enviarDoCampo() {
    const campo = document.getElementById("chatTexto");
    const botao = document.getElementById("chatEnviar");
    const texto = campo.value.trim();
    if (!texto || !chat.atual || botao.disabled) return;

    botao.disabled = true;
    try {
        const conversaId = chat.atual.id;
        // Busca antes o que chegou, para a mensagem enviada entrar na ordem certa
        await buscarMensagensNovas();
        const mensagem = await enviarMensagem(conversaId, texto);
        campo.value = "";
        ajustarAlturaDoCampo(campo);
        document.getElementById("chatContador").hidden = true;
        adicionarMensagens([mensagem], true);

        chat.atual.ultimaMensagem = mensagem.conteudo;
        chat.atual.ultimaMensagemMinha = true;
        chat.atual.ultimaMensagemEm = mensagem.enviadaEm;
        chat.conversas = [chat.atual].concat(chat.conversas.filter(function (c) { return c.id !== conversaId; }));
        renderListaDeConversas();
        document.getElementById("chatFeedback").style.display = "none";
    } catch (error) {
        showFeedback("chatFeedback", error.message, "error");
        // Pode ter sido encerrada enquanto a tela estava aberta
        if (chat.atual) {
            fetchConversa(chat.atual.id).then(function (resumo) {
                chat.atual.aberta = resumo.aberta;
                mostrarCaixaDeTexto(chat.atual);
            }).catch(function () { /* mantem como esta */ });
        }
    } finally {
        botao.disabled = false;
        campo.focus();
    }
}

// ── Datas ──────────────────────────────────────────────────────────────────

function rotuloDoDia(isoDia) {
    const hoje = hojeISO();
    const ontem = new Date();
    ontem.setDate(ontem.getDate() - 1);
    const ontemISO = ontem.getFullYear() + "-" + String(ontem.getMonth() + 1).padStart(2, "0")
        + "-" + String(ontem.getDate()).padStart(2, "0");
    if (isoDia === hoje) return "Hoje";
    if (isoDia === ontemISO) return "Ontem";
    const partes = isoDia.split("-");
    return partes[2] + "/" + partes[1] + "/" + partes[0];
}

// "14:05" para hoje (ou sempre, com soHora); "21/11" para dias anteriores
function horaCurta(isoDataHora, soHora) {
    if (!isoDataHora) return "";
    const hora = isoDataHora.substring(11, 16);
    const dia = isoDataHora.substring(0, 10);
    if (soHora || dia === hojeISO()) return hora;
    return dia.substring(8, 10) + "/" + dia.substring(5, 7);
}
