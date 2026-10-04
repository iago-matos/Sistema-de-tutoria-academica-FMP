const ICONS = {
    book:     '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
    home:     '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>',
    users:    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>',
    calendar: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>',
    star:     '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>',
    bell:     '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>',
    logout:   '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>',
    search:   '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>',
    check:    '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>',
    x:        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
    settings: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>',
    plus:     '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>'
};

const NAV_MENUS = {
    tutor: [
        { id: "inicio", label: "Início", href: "dashboard.html", icon: "home" },
        { id: "solicitacoes", label: "Solicitações", href: "solicitacoes.html", icon: "users" },
        { id: "agenda", label: "Agenda", href: "agenda.html", icon: "calendar" },
        { id: "avaliacoes", label: "Avaliações", href: "avaliacoes.html", icon: "star" }
    ],
    estudante: [
        { id: "inicio", label: "Início", href: "dashboard.html", icon: "home" },
        { id: "tutores", label: "Tutores", href: "buscar-tutores.html", icon: "users" },
        { id: "solicitacoes", label: "Solicitações", href: "meus-agendamentos.html", icon: "calendar" },
        { id: "avaliacoes", label: "Avaliações", href: "avaliacoes.html", icon: "star" }
    ],
    admin: [
        { id: "inicio", label: "Início", href: "dashboard.html", icon: "home" },
        { id: "usuarios", label: "Usuários", href: "usuarios.html", icon: "settings" }
    ]
};

function icon(name) {
    return ICONS[name] || "";
}

function getGreeting() {
    const hour = new Date().getHours();
    if (hour < 12) return "Bom dia";
    if (hour < 18) return "Boa tarde";
    return "Boa noite";
}

function formatLongDate(date) {
    const options = { weekday: "long", day: "numeric", month: "long", year: "numeric" };
    const formatted = date.toLocaleDateString("pt-BR", options);
    return formatted.charAt(0).toUpperCase() + formatted.slice(1);
}

function renderSidebar(role, activeNav, notifCount) {
    const sidebar = document.getElementById("sidebar");
    if (!sidebar) return;

    const menu = NAV_MENUS[role] || [];
    const navItems = menu.map(function (item) {
        const activeClass = item.id === activeNav ? " active" : "";
        return '<li><a class="nav-link' + activeClass + '" href="' + item.href + '">'
            + icon(item.icon)
            + "<span>" + item.label + "</span></a></li>";
    }).join("");

    const badge = notifCount > 0
        ? '<span class="notif-badge">' + notifCount + "</span>"
        : "";

    sidebar.innerHTML = '<div class="brand">'
        + '<div class="brand-mark">' + icon("book") + "</div>"
        + '<div class="brand-text"><strong>Tutoria Acadêmica</strong></div>'
        + "</div>"
        + '<nav aria-label="Menu principal"><ul class="nav-list">' + navItems + "</ul></nav>"
        + '<div class="sidebar-footer">'
        + '<a class="nav-link" href="#" data-action="notifications">' + icon("bell")
        + "<span>Notificações</span>" + badge + "</a>"
        + '<button class="nav-link" type="button" data-action="logout">'
        + icon("logout") + "<span>Sair</span></button>"
        + "</div>";

    // inject help button once
    if (!document.querySelector(".help-btn")) {
        const helpBtn = document.createElement("button");
        helpBtn.className = "help-btn";
        helpBtn.type = "button";
        helpBtn.title = "Ajuda";
        helpBtn.textContent = "?";
        document.body.appendChild(helpBtn);
    }
}

function renderPageHeader(options) {
    const header = document.getElementById("pageHeader");
    if (!header) return;

    const config = options || {};
    const user = getStoredUser();
    const firstName = user && user.nome ? user.nome.split(" ")[0] : "Usuário";

    if (config.mode === "search") {
        header.innerHTML = '<div class="page-header-row">'
            + "<div>"
            + '<h1 class="page-title">' + escapeHTML(config.title || "Tutores disponíveis") + "</h1>"
            + '<p class="page-subtitle" id="pageSubtitle">' + escapeHTML(config.subtitle || "") + "</p>"
            + "</div>"
            + '<div class="search-box">' + icon("search")
            + '<input type="search" id="searchInput" placeholder="Pesquisar..." aria-label="Pesquisar">'
            + "</div></div>";
        return;
    }

    if (config.mode === "title") {
        header.innerHTML = "<div>"
            + '<h1 class="page-title">' + escapeHTML(config.title || "") + "</h1>"
            + (config.subtitle ? '<p class="page-subtitle">' + escapeHTML(config.subtitle) + "</p>" : "")
            + "</div>";
        return;
    }

    header.innerHTML = "<div>"
        + '<h1 class="page-greeting">' + getGreeting() + ", " + escapeHTML(firstName) + " 👋</h1>"
        + '<p class="page-date">' + formatLongDate(new Date()) + "</p>"
        + "</div>";
}

function initAppShell(options) {
    const role = document.body.dataset.role;
    const activeNav = document.body.dataset.nav;
    const notifCount = options && options.notifCount ? options.notifCount : 0;

    renderSidebar(role, activeNav, notifCount);
    renderPageHeader(options && options.header ? options.header : { mode: "greeting" });
    bindLogoutButtons();

    const notifBtn = document.querySelector("[data-action='notifications']");
    if (notifBtn) {
        notifBtn.addEventListener("click", function (event) {
            event.preventDefault();
        });
    }
}

function bindSearchInput(onSearch) {
    const input = document.getElementById("searchInput");
    if (!input || typeof onSearch !== "function") return;

    input.addEventListener("input", function () {
        onSearch(input.value.trim().toLowerCase());
    });
}

function updatePageSubtitle(text) {
    setText("pageSubtitle", text);
}
