const API_PATHS = {
  readiness: "/api/catalog/readiness",
  effectiveSpec: (categoryCode) => `/api/catalog/effective-spec/${encodeURIComponent(categoryCode)}`,
  refreshStatus: "/api/catalog/governance/refresh-status",
  sources: "/api/catalog/governance/sources",
  refreshRuns: "/api/catalog/governance/refresh-runs",
  modelEnrichmentQueue: "/api/catalog/governance/model-enrichment-queue",
  modelEnrichmentPromote: "/api/catalog/governance/model-enrichment-promote",
  reviewQueue: "/api/catalog/governance/review-queue",
  reviewAction: "/api/catalog/governance/review-action",
  publishEvents: "/api/catalog/governance/publish-events",
  refresh: "/api/catalog/governance/refresh",
  rebuild: "/api/catalog/governance/rebuild",
  liveValues: "/api/catalog/live-values",
  searchWithFacets: "/api/offers/searchWithFacets",
};

const DEFAULT_CATEGORY_CODE = "TECH.PHONES";
const DEFAULT_RUNTIME_ATTRIBUTE_CODES = [
  "model",
  "color",
  "memory_gb",
  "ram_gb",
  "network_type",
  "release_year",
  "screen_size_inch",
  "battery_mah",
  "wired_charging_w",
  "wireless_charging",
  "esim_support",
  "ip_rating",
];

const PHONES_ATTRIBUTE_PLAYBOOK = [
  { code: "model", label: "Модель", usage: "facet", priority: "identity" },
  { code: "color", label: "Цвет", usage: "facet", priority: "primary" },
  { code: "memory_gb", label: "Память", usage: "facet", priority: "primary" },
  { code: "ram_gb", label: "ОЗУ", usage: "facet", priority: "secondary" },
  { code: "network_type", label: "Сеть", usage: "facet", priority: "secondary" },
  { code: "release_year", label: "Год релиза", usage: "sort/badge", priority: "rich" },
  { code: "screen_size_inch", label: "Экран", usage: "compare", priority: "rich" },
  { code: "battery_mah", label: "Батарея", usage: "badge/compare", priority: "rich" },
  { code: "wired_charging_w", label: "Проводная зарядка", usage: "badge/compare", priority: "rich" },
  { code: "wireless_charging", label: "Беспроводная зарядка", usage: "facet/badge", priority: "rich" },
  { code: "esim_support", label: "eSIM", usage: "facet/badge", priority: "rich" },
  { code: "ip_rating", label: "IP-защита", usage: "badge/compare", priority: "rich" },
];

const PHONE_BRAND_HINTS_BY_CODE = {
  APPLE: ["apple", "эпл", "iphone", "айфон"],
  GOOGLE: ["google", "гугл", "pixel", "пиксель"],
  HONOR: ["honor", "хонор"],
  HUAWEI: ["huawei", "хуавей", "хуавэй"],
  NOTHING: ["nothing", "натинг"],
  ONEPLUS: ["oneplus", "ванплас", "уанплас", "nord", "норд"],
  REALME: ["realme", "риалми"],
  SAMSUNG: ["samsung", "самсунг", "galaxy", "галакси"],
  XIAOMI: ["xiaomi", "ксиаоми", "сяоми", "redmi", "редми", "poco", "поко"],
};

const QUERY_FLOW_SAMPLE_QUERIES = [
  "iphone 17 pro 256gb",
  "galaxy s24+ 512gb 120hz",
  "pixel 9 pro 256gb tensor g4",
  "nothing phone 2a 45w",
  "oneplus 13 esim",
  "xiaomi 14 512gb",
];

const SECTION_DEFS = [
  { key: "overview", label: "Overview" },
  { key: "branches", label: "Branches" },
  { key: "sources", label: "Sources" },
  { key: "enrichment", label: "Enrichment" },
  { key: "runs", label: "Runs" },
  { key: "review", label: "Review" },
  { key: "publish", label: "Publish" },
  { key: "runtime-lab", label: "Runtime Lab" },
  { key: "query-flow", label: "Query Flow" },
];

const SECTION_HELP = {
  overview: {
    title: "Как пользоваться обзором",
    bullets: [
      "Сначала выберите ветку слева. Overview сразу покажет readiness, refresh health и следующий операторский шаг.",
      "Если ветка красная, открывайте blocker-карточку и переходите в нужный раздел одним кликом.",
      "Используйте быстрые действия только для активной ветки: refresh matched sources или rebuild.",
    ],
  },
  branches: {
    title: "Как читать coverage веток",
    bullets: [
      "Таблица показывает продуктовую зрелость, а не только технический статус.",
      "Красные ветки означают, что до продового качества не хватает моделей, атрибутов или operational health.",
      "Клик по строке сразу делает ветку активной и открывает структурированные детали в инспекторе.",
    ],
  },
  sources: {
    title: "Как работать с источниками",
    bullets: [
      "Запускайте refresh по одному source, когда надо локализовать проблему без общего шума.",
      "Смотрите matched sources, last run и publish result перед повторным прогоном.",
      "Если source падает, открывайте карточку в инспекторе и смотрите summary, а не сырой лог целиком.",
    ],
  },
  enrichment: {
    title: "Как работать с queue новых моделей",
    bullets: [
      "Monitoring означает, что runtime уже видит модель, но сигнал ещё сырой или бренд/source не готовы для official enrichment.",
      "Ready for official enrichment означает, что модель уже набрала нужный runtime объём и может быть переведена в official endpoint overlay.",
      "Promote here не меняет json seed вручную: оператор создаёт DB overlay endpoint и при желании сразу триггерит refresh нужного source.",
    ],
  },
  runs: {
    title: "Как читать refresh runs",
    bullets: [
      "Runs показывают не stacktrace, а результат цепочки: extract -> normalize -> review -> publish.",
      "Открывайте только failed или suspicious runs, чтобы не тратить время на зелёные проходы.",
      "Если error summary выглядит как schema gap, переходите в publish log и runtime lab.",
    ],
  },
  review: {
    title: "Как работать с review queue",
    bullets: [
      "Approve используйте, когда кандидат корректен, но ещё не должен публиковаться в runtime.",
      "Promote используйте, когда кандидат готов сразу попасть в published serving/runtime.",
      "Reject используйте только если уверены, что это шум источника или неверный alias/value.",
    ],
  },
  publish: {
    title: "Как читать publish log",
    bullets: [
      "Publish log отвечает на вопрос: что реально ушло в serving/runtime, а не что просто нашли в source.",
      "Смотрите artifact type, status и details summary. Полный JSON всегда доступен в инспекторе.",
      "Повторяющиеся ошибки здесь обычно означают schema/contract gap, а не проблему конкретного клика.",
    ],
  },
  "runtime-lab": {
    title: "Как использовать Runtime Lab",
    bullets: [
      "Проверяйте published known values и aliases без мобильного приложения и без seed-гадания.",
      "Сначала задайте ветку, затем при необходимости уточните brand/model и выберите атрибуты.",
      "Если runtime universe не совпадает с ожиданием UI, проблема почти всегда в publish/coverage, а не в Compose.",
    ],
  },
  "query-flow": {
    title: "Как использовать Query Flow",
    bullets: [
      "Вводите реальный пользовательский запрос, а не выдуманный технический кейс.",
      "Секция сначала строит runtime-backed гипотезу brand/model/attrs, затем прогоняет market-check через searchWithFacets.",
      "Если модель или атрибут не распознаются здесь, чаще всего не хватает aliases, known values или published runtime coverage.",
    ],
  },
};

const state = {
  activeSection: localStorage.getItem("admin.activeSection") || "overview",
  openHelpKey: null,
  backendConfig: null,
  connection: {
    status: "unknown",
    message: "Control plane ещё не проверен.",
    context: "",
    httpStatus: null,
    lastSuccessAt: null,
    lastFailureAt: null,
  },
  categories: [],
  readiness: [],
  selectedCategory: localStorage.getItem("admin.selectedCategory") || DEFAULT_CATEGORY_CODE,
  refreshStatus: null,
  branchSpec: null,
  branchRuntimeSnapshot: null,
  sources: [],
  modelEnrichmentQueue: [],
  runs: [],
  reviewQueue: [],
  publishEvents: [],
  runtimeLab: {
    brand: "",
    model: "",
    locale: "ru-RU",
    attributeCodesText: DEFAULT_RUNTIME_ATTRIBUTE_CODES.join(", "),
    snapshot: null,
  },
  queryFlow: {
    rawQuery: "",
    locale: "ru-RU",
    attributeCodesText: DEFAULT_RUNTIME_ATTRIBUTE_CODES.join(", "),
    result: null,
  },
  detailRegistry: new Map(),
  selectedDetailKey: null,
  loadingBranch: false,
  lastLoadedAt: null,
  filters: {
    branchSearch: "",
    sourceSearch: "",
    enrichmentStatus: "ALL",
    runStatus: "ALL",
    publishStatus: "ALL",
    contractStatus: "ALL",
  },
  enrichmentDrafts: {},
};

const refs = {};

document.addEventListener("DOMContentLoaded", bootstrap);

async function bootstrap() {
  refs.shell = document.querySelector(".shell");
  refs.nav = document.getElementById("nav");
  refs.sectionTitle = document.getElementById("section-title");
  refs.sectionHelp = document.getElementById("section-help");
  refs.sectionHelpButton = document.getElementById("section-help-button");
  refs.sectionContext = document.getElementById("section-context");
  refs.content = document.getElementById("content");
  refs.banner = document.getElementById("banner");
  refs.backendStatus = document.getElementById("backend-status");
  refs.drawer = document.getElementById("detail-drawer");
  refs.drawerTitle = document.getElementById("drawer-title");
  refs.drawerBody = document.getElementById("drawer-body");
  refs.toast = document.getElementById("toast");
  refs.categorySelect = document.getElementById("category-select");
  refs.categoryStatus = document.getElementById("category-status");
  refs.backendUrlInput = document.getElementById("backend-url-input");

  document.body.addEventListener("click", handleClick);
  document.body.addEventListener("change", handleChange);
  document.body.addEventListener("input", handleInput);

  await loadBackendConfig();
  try {
    await loadReadinessInventory();
  } catch (error) {
    state.readiness = [];
    state.categories = state.selectedCategory ? [state.selectedCategory] : [];
    renderCategorySelect();
    showToast(error.message || "Не удалось загрузить inventory веток", "danger");
  }
  await loadBranchData();
  render();
}

async function loadBackendConfig() {
  state.backendConfig = await fetchJson("/__admin/config");
  refs.backendUrlInput.value = state.backendConfig.backendUrl || "";
  state.connection.message = `Ожидаем ответ от ${state.backendConfig.backendUrl}`;
}

async function loadReadinessInventory() {
  const readiness = await apiGet(API_PATHS.readiness);
  state.readiness = Array.isArray(readiness) ? readiness : [];
  state.categories = state.readiness
    .map((item) => item.category?.code)
    .filter(Boolean)
    .sort((left, right) => left.localeCompare(right, "en"));
  if (!state.categories.includes(state.selectedCategory) && state.categories.length > 0) {
    state.selectedCategory = state.categories.includes(DEFAULT_CATEGORY_CODE)
      ? DEFAULT_CATEGORY_CODE
      : state.categories[0];
  }
  renderCategorySelect();
}

async function loadBranchData() {
  if (!state.selectedCategory) return;
  state.loadingBranch = true;
  render();
  try {
    const [refreshStatus, sources, modelEnrichmentQueue, runs, reviewQueue, publishEvents, branchSpec, branchRuntimeSnapshot] = await Promise.all([
      apiGet(API_PATHS.refreshStatus, { categoryCode: state.selectedCategory }),
      apiGet(API_PATHS.sources, { categoryCode: state.selectedCategory }),
      apiGet(API_PATHS.modelEnrichmentQueue, { categoryCode: state.selectedCategory, limit: 100 }),
      apiGet(API_PATHS.refreshRuns, { categoryCode: state.selectedCategory, limit: 25 }),
      apiGet(API_PATHS.reviewQueue, { categoryCode: state.selectedCategory, limit: 25 }),
      apiGet(API_PATHS.publishEvents, { categoryCode: state.selectedCategory, limit: 25 }),
      safeApiGet(API_PATHS.effectiveSpec(state.selectedCategory), {}, null),
      safeApiGet(API_PATHS.liveValues, {
        categoryCode: state.selectedCategory,
        locale: state.runtimeLab.locale || "ru-RU",
        attributeCode: DEFAULT_RUNTIME_ATTRIBUTE_CODES,
      }, null),
    ]);
    state.refreshStatus = refreshStatus;
    state.sources = Array.isArray(sources) ? sources : [];
    state.modelEnrichmentQueue = Array.isArray(modelEnrichmentQueue) ? modelEnrichmentQueue : [];
    state.runs = Array.isArray(runs) ? runs : [];
    state.reviewQueue = Array.isArray(reviewQueue) ? reviewQueue : [];
    state.publishEvents = Array.isArray(publishEvents) ? publishEvents : [];
    state.branchSpec = branchSpec;
    state.branchRuntimeSnapshot = branchRuntimeSnapshot;
    state.queryFlow.result = null;
    state.lastLoadedAt = Date.now();
    if (!state.runtimeLab.brand) {
      state.runtimeLab.brand = inferRuntimeBrand();
    }
    if (!state.runtimeLab.snapshot) {
      state.runtimeLab.snapshot = branchRuntimeSnapshot;
    }
  } catch (error) {
    resetBranchDataAfterFailure();
    showToast(error.message || "Не удалось загрузить ветку", "danger");
  } finally {
    state.loadingBranch = false;
  }
}

function render() {
  renderNav();
  renderSectionHeader();
  renderBackendStatus();
  renderBanner();
  renderContent();
  renderDrawer();
}

function renderNav() {
  const counts = {
    branches: countCriticalBranches(),
    sources: state.sources.length,
    enrichment: state.modelEnrichmentQueue.length,
    runs: state.runs.length,
    review: state.reviewQueue.length,
    publish: state.publishEvents.length,
    "runtime-lab": state.runtimeLab.snapshot ? Object.keys(state.runtimeLab.snapshot.knownValuesByAttributeCode || {}).length : 0,
    "query-flow": state.queryFlow.result?.matchedAttributes?.length || 0,
  };
  refs.nav.innerHTML = SECTION_DEFS.map((section) => {
    const active = state.activeSection === section.key ? " is-active" : "";
    const count = counts[section.key];
    const suffix = count ? `<span class="nav__item-count">${escapeHtml(String(count))}</span>` : "";
    return `
      <button class="nav__item${active}" data-action="switch-section" data-section="${section.key}">
        <span>${escapeHtml(section.label)}</span>
        ${suffix}
      </button>
    `;
  }).join("");
}

function renderCategorySelect() {
  const categories = state.categories.length > 0
    ? state.categories
    : [state.selectedCategory || DEFAULT_CATEGORY_CODE];
  refs.categorySelect.innerHTML = categories
    .map((categoryCode) => {
      const selected = categoryCode === state.selectedCategory ? " selected" : "";
      return `<option value="${escapeHtml(categoryCode)}"${selected}>${escapeHtml(categoryCode)}</option>`;
    })
    .join("");
  if (refs.categoryStatus) {
    const total = state.categories.length;
    refs.categoryStatus.textContent = total > 0
      ? `Загружено веток: ${total}. Активная: ${state.selectedCategory || "—"}.`
      : `Inventory веток не загружен. Используем fallback: ${state.selectedCategory || DEFAULT_CATEGORY_CODE}.`;
  }
}

function renderSectionHeader() {
  const title = SECTION_DEFS.find((item) => item.key === state.activeSection)?.label || "Overview";
  refs.sectionTitle.textContent = title;
  const help = SECTION_HELP[state.activeSection];
  const isOpen = Boolean(help) && state.openHelpKey === state.activeSection;
  if (refs.sectionHelpButton) {
    refs.sectionHelpButton.dataset.helpKey = state.activeSection;
    refs.sectionHelpButton.hidden = !help;
    refs.sectionHelpButton.setAttribute("aria-expanded", isOpen ? "true" : "false");
  }
  if (refs.sectionHelp) {
    refs.sectionHelp.innerHTML = help ? renderSectionHelpPopover(help) : "";
    refs.sectionHelp.className = isOpen
      ? "context-help"
      : "context-help context-help--hidden";
  }
  renderSectionContext();
}

function renderSectionHelpPopover(help) {
  return `
    <h3 class="context-help__title">${escapeHtml(help.title)}</h3>
    <ul class="context-help__list">
      ${help.bullets.map((bullet) => `<li>${escapeHtml(bullet)}</li>`).join("")}
    </ul>
  `;
}

function renderSectionContext() {
  if (!refs.sectionContext) return;
  const selectedReadiness = getSelectedReadiness();
  const operational = collectOperationalSnapshot();
  const latestRun = operational.latestRun;
  const latestRunHistoricalFailure = latestRun && isRunFailure(latestRun) && !(operational.activeFailure?.kind === "run" && operational.activeFailure.payload?.id === latestRun.id);
  const items = [
    `<span class="chip">branch=${escapeHtml(state.selectedCategory || "—")}</span>`,
    selectedReadiness
      ? `<span class="chip">readiness=${escapeHtml(selectedReadiness.readiness)}</span>`
      : `<span class="chip">readiness=—</span>`,
    `<span class="chip">sources=${escapeHtml(String(state.sources.length))}</span>`,
    `<span class="chip">enrichment=${escapeHtml(String(state.modelEnrichmentQueue.length))}</span>`,
    latestRun
      ? `<span class="chip">last=${escapeHtml(latestRunHistoricalFailure ? "HISTORY" : [latestRun.status, latestRun.publishStatus].filter(Boolean).join("/"))}</span>`
      : `<span class="chip">last=—</span>`,
    `<span class="chip">review=${escapeHtml(String(state.reviewQueue.length))}</span>`,
  ];
  refs.sectionContext.innerHTML = items.join("");
}

function renderBackendStatus() {
  if (!refs.backendStatus) return;
  const summary = connectionSummary();
  refs.backendStatus.innerHTML = `
    <div class="backend-status__row">
      ${renderStatusChip(summary.label, summary.tone)}
      <span class="backend-status__text">${escapeHtml(summary.text)}</span>
    </div>
  `;
}

function renderControlPlaneUnavailableSection({ title, text, endpoint }) {
  const summary = connectionSummary();
  return `
    <section class="panel offline-card">
      <div class="panel__header">
        <div>
          <div class="table-card__eyebrow">Control plane</div>
          <h3 class="panel__title">${escapeHtml(title)}</h3>
        </div>
        ${renderStatusChip(summary.label, summary.tone)}
      </div>
      <div class="panel__subtext">${escapeHtml(text)}</div>
      <div class="chips">
        <span class="chip">backend=${escapeHtml(state.backendConfig?.backendUrl || "—")}</span>
        <span class="chip">endpoint=${escapeHtml(endpoint || "—")}</span>
        ${state.connection.httpStatus ? `<span class="chip">http=${escapeHtml(String(state.connection.httpStatus))}</span>` : ""}
        ${state.connection.context ? `<span class="chip">context=${escapeHtml(state.connection.context)}</span>` : ""}
      </div>
      <ol class="offline-card__steps">
        <li>Проверь, что нужный backend действительно запущен и это не другой сервис на <code>127.0.0.1:8080</code>.</li>
        <li>Убедись, что backend отвечает маршрутом <code>${escapeHtml(endpoint || API_PATHS.readiness)}</code>.</li>
        <li>После фикса нажми <code>Повторить загрузку</code>, а затем вернись к coverage/runtime/UI-аудиту.</li>
      </ol>
      <div class="panel__toolbar">
        <button class="button button--primary" data-action="reload-all">Повторить загрузку</button>
        <button class="button button--ghost" data-action="save-backend-url">Сохранить backend URL ещё раз</button>
      </div>
    </section>
  `;
}

function renderBanner() {
  const connectionAlert = deriveConnectionAlert();
  if (connectionAlert) {
    refs.banner.className = `banner banner--${connectionAlert.tone}`;
    refs.banner.innerHTML = `
      <h3>${escapeHtml(connectionAlert.title)}</h3>
      <p>${escapeHtml(connectionAlert.text)}</p>
      <div class="banner__actions">
        <button class="button button--ghost" data-action="reload-all">Повторить загрузку</button>
        <button class="button button--ghost" data-action="switch-section" data-section="sources">Открыть Sources</button>
      </div>
    `;
    return;
  }

  const selectedReadiness = getSelectedReadiness();
  const operational = collectOperationalSnapshot();
  const activeFailure = operational.activeFailure;
  const activeFailureDiagnosis = operationalEntryDiagnosis(activeFailure);
  let tone = "";
  let title = "";
  let text = "";
  let actionList = [];

  if (activeFailureDiagnosis) {
    tone = "danger";
    title = activeFailureDiagnosis.title;
    text = activeFailureDiagnosis.summary;
    actionList = activeFailureDiagnosis.actions;
  } else if (activeFailure) {
    tone = "danger";
    title = activeFailure.kind === "publish"
      ? "Последний rebuild/publish завершился с ошибкой"
      : "Последний refresh завершился с ошибкой";
    text = operationalEntrySummary(activeFailure);
  } else if (selectedReadiness && selectedReadiness.readiness !== "READY") {
    tone = "warning";
    title = `Ветка ${state.selectedCategory} ещё не READY`;
    text = (selectedReadiness.blockingIssues || []).slice(0, 3).join(" • ") || "Проверь coverage и operational blockers.";
  } else if (countReadyEnrichmentCandidates() > 0) {
    tone = "warning";
    title = "Есть модели, готовые к official enrichment";
    text = `В runtime queue сейчас ${countReadyEnrichmentCandidates()} ready-кандидат(ов). Их уже можно переводить в official source overlay.`;
  } else if (state.reviewQueue.length > 0) {
    tone = "warning";
    title = "Есть элементы в review queue";
    text = `Сейчас требуется решение по ${state.reviewQueue.length} кандидату(ам).`;
  }

  if (!tone) {
    refs.banner.className = "banner banner--hidden";
    refs.banner.innerHTML = "";
    return;
  }

  refs.banner.className = `banner banner--${tone}`;
  refs.banner.innerHTML = `
    <h3>${escapeHtml(title)}</h3>
    <p>${escapeHtml(text)}</p>
    ${actionList.length > 0 ? `
      <ul class="banner__list">
        ${actionList.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}
      </ul>
    ` : ""}
  `;
}

function renderContent() {
  switch (state.activeSection) {
    case "overview":
      refs.content.innerHTML = renderOverviewSection();
      break;
    case "branches":
      refs.content.innerHTML = renderBranchesSection();
      break;
    case "sources":
      refs.content.innerHTML = renderSourcesSection();
      break;
    case "enrichment":
      refs.content.innerHTML = renderEnrichmentSection();
      break;
    case "runs":
      refs.content.innerHTML = renderRunsSection();
      break;
    case "review":
      refs.content.innerHTML = renderReviewSection();
      break;
    case "publish":
      refs.content.innerHTML = renderPublishSection();
      break;
    case "runtime-lab":
      refs.content.innerHTML = renderRuntimeLabSection();
      break;
    case "query-flow":
      refs.content.innerHTML = renderQueryFlowSection();
      break;
    default:
      refs.content.innerHTML = "";
  }
}

function renderOverviewSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Control plane не дал данных",
      text: "Сейчас проблема не в ветке и не в coverage, а в том, что admin-web не видит живой catalog backend. Пока это не починить, Overview будет только имитировать нули.",
      endpoint: API_PATHS.readiness,
    });
  }
  const selectedReadiness = getSelectedReadiness();
  const operational = collectOperationalSnapshot();
  const latestRun = operational.latestRun;
  const latestPublish = operational.latestPublish;
  const metrics = [
    {
      eyebrow: "Веток в inventory",
      value: state.readiness.length,
      status: `${countReadyBranches()} READY`,
      tone: "blue",
      subtext: `${countCriticalBranches()} требуют внимания прямо сейчас.`,
    },
    {
      eyebrow: "Активная ветка",
      value: state.selectedCategory,
      status: selectedReadiness ? selectedReadiness.readiness : "UNKNOWN",
      tone: toneForReadiness(selectedReadiness?.readiness),
      subtext: readinessSummary(selectedReadiness),
    },
    {
      eyebrow: "Matched sources",
      value: state.refreshStatus?.matchedSourceCount ?? 0,
      status: state.refreshStatus?.enabled ? "Scheduler ON" : "Scheduler OFF",
      tone: state.refreshStatus?.enabled ? "green" : "amber",
      subtext: `Из ${state.refreshStatus?.availableSourceCount ?? 0} доступных источников.`,
    },
    {
      eyebrow: "Review queue",
      value: state.reviewQueue.length,
      status: state.reviewQueue.length === 0 ? "Clean" : "Action required",
      tone: state.reviewQueue.length === 0 ? "green" : "amber",
      subtext: "Новые кандидаты, конфликты и неоднозначные value/alias.",
    },
  ];

  const nextAction = deriveNextAction(selectedReadiness, operational);
  return `
    <section class="grid grid--metrics">
      ${metrics.map(renderMetricCard).join("")}
    </section>

    <section class="split">
      <div class="panel">
        <div class="panel__header">
          <div>
            <div class="table-card__eyebrow">Branch focus</div>
            <h3 class="panel__title">${escapeHtml(state.selectedCategory)}</h3>
          </div>
          ${renderStatusChip(selectedReadiness?.readiness || "UNKNOWN")}
        </div>
        <div class="panel__subtext">${escapeHtml(readinessSummary(selectedReadiness))}</div>
        <div class="chips" style="margin-top:14px;">
          ${(selectedReadiness?.blockingIssues || []).slice(0, 6).map((issue) => `<span class="chip">${escapeHtml(issue)}</span>`).join("") || `<span class="chip">Блокирующих issue нет</span>`}
        </div>
        <div class="panel__toolbar" style="margin-top:18px;">
          <button class="button button--primary" data-action="switch-section" data-section="sources">Перейти к источникам</button>
          <button class="button button--ghost" data-action="switch-section" data-section="runs">Открыть runs</button>
          <button class="button button--ghost" data-action="switch-section" data-section="runtime-lab">Открыть runtime lab</button>
        </div>
      </div>

      <div class="panel">
        <div class="panel__header">
          <div>
            <div class="table-card__eyebrow">Next action</div>
            <h3 class="panel__title">${escapeHtml(nextAction.title)}</h3>
          </div>
          ${renderStatusChip(nextAction.tone.toUpperCase())}
        </div>
        <div class="panel__subtext">${escapeHtml(nextAction.description)}</div>
        <div class="meta-list" style="margin-top:14px;">
          <span class="meta">Latest run: ${escapeHtml(summarizeRun(latestRun))}</span>
          <span class="meta">Latest publish: ${escapeHtml(summarizePublish(latestPublish))}</span>
        </div>
      </div>
    </section>
  `;
}

function renderBranchesSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Inventory веток недоступен",
      text: "Branches опирается на readiness inventory. Пока backend не отвечает ожидаемым API, список веток нельзя считать достоверным.",
      endpoint: API_PATHS.readiness,
    });
  }
  const search = (state.filters.branchSearch || "").toLowerCase();
  const rows = state.readiness
    .filter((item) => {
      const haystack = `${item.category?.code || ""} ${categoryDisplayName(item.category)}`.toLowerCase();
      return haystack.includes(search);
    })
    .sort(sortReadinessRows);
  const genericCoverage = renderGenericBranchContractSection();
  const phonesCoverage = isPhonesBranch() ? renderPhonesCoverageSection() : "";

  return `
    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">Coverage matrix</div>
          <h3 class="table-card__title">Ветки и readiness</h3>
        </div>
        <div class="status-stack">
          ${renderStatusChip(`${countCriticalBranches()} CRITICAL`)}
        </div>
      </div>
      <div class="table-card__subtext">Coverage измеряется не количеством моделей, а качеством product master, completeness gate и operational health.</div>
      <div class="table-card__toolbar">
        <input class="field__input" type="search" data-input="branch-search" placeholder="Фильтр по ветке" value="${escapeHtml(state.filters.branchSearch)}">
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Ветка</th>
              <th>Readiness</th>
              <th>Editorial</th>
              <th>Operational</th>
              <th>Completeness</th>
              <th>Blockers</th>
            </tr>
          </thead>
          <tbody>
            ${rows.map((item) => renderBranchRow(item)).join("")}
          </tbody>
        </table>
      </div>
    </section>

    ${genericCoverage}
    ${phonesCoverage}
  `;
}

function renderSourcesSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Source registry недоступен",
      text: "Сейчас нельзя понять, есть ли у ветки реальные official sources, потому что backend не отдал registry/status.",
      endpoint: API_PATHS.sources,
    });
  }
  const search = (state.filters.sourceSearch || "").toLowerCase();
  const filteredSources = state.sources.filter((source) => {
    const haystack = `${source.displayName} ${source.registryCode} ${source.sourceCode} ${source.metadata?.brand || ""}`.toLowerCase();
    return haystack.includes(search);
  });

  return `
    <section class="panel">
      <div class="panel__header">
        <div>
          <div class="table-card__eyebrow">Scheduler</div>
          <h3 class="panel__title">Loop status</h3>
        </div>
        ${renderStatusChip(state.refreshStatus?.enabled ? "ENABLED" : "DISABLED")}
      </div>
      <div class="chips">
        <span class="chip">configured=${escapeHtml(state.refreshStatus?.configuredCategoryCode || "—")}</span>
        <span class="chip">effective=${escapeHtml(state.refreshStatus?.effectiveCategoryCode || "—")}</span>
        <span class="chip">interval=${escapeHtml(formatDurationShort(state.refreshStatus?.pollIntervalMs))}</span>
        <span class="chip">matched=${escapeHtml(String(state.refreshStatus?.matchedSourceCount ?? 0))}/${escapeHtml(String(state.refreshStatus?.availableSourceCount ?? 0))}</span>
      </div>
    </section>

    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">Source registry</div>
          <h3 class="table-card__title">Источники активной ветки</h3>
        </div>
      </div>
      <div class="table-card__toolbar">
        <input class="field__input" type="search" data-input="source-search" placeholder="Фильтр по source" value="${escapeHtml(state.filters.sourceSearch)}">
      </div>
      <div class="grid grid--cards">
        ${filteredSources.map((source) => renderSourceCard(source)).join("") || renderEmptyState("Для этой ветки нет источников", "Проверь registry или выбери другую ветку.")}
      </div>
    </section>
  `;
}

function renderEnrichmentSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Model enrichment queue недоступна",
      text: "Сейчас admin-web не видит runtime queue новых phone-моделей, поэтому нельзя ни отслеживать кандидатов, ни переводить их в official enrichment.",
      endpoint: API_PATHS.modelEnrichmentQueue,
    });
  }
  const queue = getFilteredModelEnrichmentQueue();
  const readyCount = countReadyEnrichmentCandidates();
  const monitoringCount = state.modelEnrichmentQueue.filter((item) => item.status === "MONITORING").length;
  const enrichedCount = state.modelEnrichmentQueue.filter((item) => item.status === "ENRICHED").length;
  const rejectedCount = state.modelEnrichmentQueue.filter((item) => item.status === "REJECTED").length;

  return `
    <section class="grid grid--metrics">
      ${[
        {
          eyebrow: "Queue",
          value: state.modelEnrichmentQueue.length,
          status: readyCount > 0 ? "ACTION REQUIRED" : "MONITORING",
          tone: readyCount > 0 ? "amber" : "blue",
          subtext: "Runtime-сигналы новых моделей и модельных хвостов.",
        },
        {
          eyebrow: "Ready",
          value: readyCount,
          status: readyCount > 0 ? "PROMOTE" : "EMPTY",
          tone: readyCount > 0 ? "green" : "slate",
          subtext: "Кандидаты с валидным runtime объёмом и official source.",
        },
        {
          eyebrow: "Monitoring",
          value: monitoringCount,
          status: monitoringCount > 0 ? "COLLECTING" : "EMPTY",
          tone: monitoringCount > 0 ? "blue" : "slate",
          subtext: "Сырые сигналы, которым ещё не хватает brand/source/confidence/volume.",
        },
        {
          eyebrow: "Enriched / rejected",
          value: `${enrichedCount}/${rejectedCount}`,
          status: "CLOSED",
          tone: rejectedCount > 0 ? "amber" : "green",
          subtext: "Уже переведённые в official source или отфильтрованные шумы.",
        },
      ].map(renderMetricCard).join("")}
    </section>

    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">Runtime-triggered queue</div>
          <h3 class="table-card__title">Новые модели и их сигналы</h3>
        </div>
        ${renderStatusChip(`${readyCount} ready`, readyCount > 0 ? "amber" : "green")}
      </div>
      <div class="table-card__subtext">
        Здесь видны runtime-сигналы, по которым ветка набирает новых phone-кандидатов. Для ready-кандидатов можно завести official endpoint overlay без ручной правки json.
      </div>
      <div class="table-card__toolbar">
        <select class="field__input" data-change="enrichment-status-filter">
          ${["ALL", "READY_FOR_OFFICIAL_ENRICHMENT", "MONITORING", "ENRICHED", "REJECTED"].map((status) => `<option value="${status}"${state.filters.enrichmentStatus === status ? " selected" : ""}>${status}</option>`).join("")}
        </select>
        <button class="button button--ghost" data-action="switch-section" data-section="sources">Открыть Sources</button>
        <button class="button button--ghost" data-action="reload-all">Обновить queue</button>
      </div>
      <div class="grid grid--cards">
        ${queue.map((candidate) => renderEnrichmentCandidateCard(candidate)).join("") || renderEmptyState("Queue пуста", "Пока runtime не принёс новых phone-кандидатов для этой ветки.")}
      </div>
    </section>
  `;
}

function renderGenericBranchContractSection() {
  if (!state.branchSpec) {
    return `
      <section class="table-card">
        <div class="table-card__header">
          <div>
            <div class="table-card__eyebrow">Branch contract</div>
            <h3 class="table-card__title">Атрибуты и фасеты активной ветки</h3>
          </div>
        </div>
        ${renderEmptyState("Effective spec ещё не загружен", "Для активной ветки backend не вернул contract. Без него нельзя качественно покрывать attr→facet связь.")}
      </section>
    `;
  }

  const rows = buildBranchContractRows(state.branchSpec);
  const identityCount = rows.filter((row) => row.isIdentity).length;
  const facetCount = rows.filter((row) => row.facetEnabled).length;
  const requiredSearchCount = rows.filter((row) => row.requiredForSearch).length;
  const systemCount = rows.filter((row) => row.kind === "system").length;
  const contractGaps = buildGenericContractGaps(rows, state.branchSpec);

  return `
    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">Branch contract</div>
          <h3 class="table-card__title">Атрибуты и фасеты активной ветки</h3>
        </div>
        ${renderStatusChip(contractGaps.length === 0 ? "HEALTHY" : `${contractGaps.length} GAPS`, contractGaps.length === 0 ? "green" : "amber")}
      </div>
      <div class="grid grid--cards" style="margin-bottom:16px;">
        ${renderMetricCard({
          eyebrow: "Всего атрибутов",
          value: rows.length,
          status: `${systemCount} system`,
          tone: "blue",
          subtext: "Effective spec активной ветки.",
        })}
        ${renderMetricCard({
          eyebrow: "Identity",
          value: identityCount,
          status: identityCount > 0 ? "PRESENT" : "MISSING",
          tone: identityCount > 0 ? "green" : "red",
          subtext: "Критично для нормального product master.",
        })}
        ${renderMetricCard({
          eyebrow: "Facet-ready",
          value: facetCount,
          status: facetCount > 0 ? "ENABLED" : "OFF",
          tone: facetCount > 0 ? "green" : "amber",
          subtext: "Связь attr→facet для ветки.",
        })}
        ${renderMetricCard({
          eyebrow: "Required for search",
          value: requiredSearchCount,
          status: requiredSearchCount > 0 ? "CONFIGURED" : "THIN",
          tone: requiredSearchCount > 0 ? "blue" : "amber",
          subtext: "Что реально требуется для поиска.",
        })}
      </div>

      <div class="split">
        <div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Атрибут</th>
                  <th>Kind</th>
                  <th>Facet</th>
                  <th>Identity</th>
                  <th>Required</th>
                  <th>Data</th>
                </tr>
              </thead>
              <tbody>
                ${rows.map((row) => renderBranchContractRow(row)).join("")}
              </tbody>
            </table>
          </div>
        </div>
        <div class="detail-card">
          <div class="table-card__eyebrow">Operator focus</div>
          <h3 class="panel__title">Что мешает качественно покрывать ветку</h3>
          <div class="panel__subtext">
            Этот блок даёт branch-first сигнал по связи атрибута и фасета. Здесь нужно смотреть, прежде чем лить новые модели или значения.
          </div>
          <div class="recommendation-list" style="margin-top:16px;">
            ${contractGaps.map((gap) => `
              <div class="recommendation-card">
                <strong>${escapeHtml(gap.title)}</strong>
                <div class="panel__subtext">${escapeHtml(gap.description)}</div>
              </div>
            `).join("") || `
              <div class="recommendation-card">
                <strong>Контракт ветки выглядит связным</strong>
                <div class="panel__subtext">Можно добивать coverage моделей, values, aliases и runtime refresh уже без blind spots по attr→facet.</div>
              </div>
            `}
          </div>
          <div class="lab-card__actions" style="margin-top:16px;">
            <button class="button button--ghost" data-action="switch-section" data-section="runtime-lab">Открыть Runtime Lab</button>
            <button class="button button--ghost" data-action="switch-section" data-section="query-flow">Открыть Query Flow</button>
          </div>
        </div>
      </div>
    </section>
  `;
}

function renderRunsSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Refresh runs недоступны",
      text: "Runs нельзя анализировать, пока control plane не отвечает. Сначала почини backend URL или сам server.",
      endpoint: API_PATHS.refreshRuns,
    });
  }
  const filteredRuns = state.runs.filter((run) => state.filters.runStatus === "ALL" || run.status === state.filters.runStatus || run.publishStatus === state.filters.runStatus);
  const metrics = buildRunMetrics(state.runs, filteredRuns, state.publishEvents);
  return `
    <section class="grid grid--metrics">
      ${metrics.map((metric) => renderCompactMetricCard(metric)).join("")}
    </section>

    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">Refresh executions</div>
          <h3 class="table-card__title">Runs</h3>
          <div class="table-card__subtext">Сначала смотри failed и suspicious runs. Зелёные проходы оставляй на потом.</div>
        </div>
        ${renderStatusChip(filteredRuns.length === 0 ? "EMPTY" : `${filteredRuns.length} VISIBLE`, filteredRuns.length === 0 ? "slate" : "blue")}
      </div>
      <div class="table-card__toolbar">
        <select class="field__input" data-change="run-status-filter">
          ${["ALL", "COMPLETED", "FAILED", "PUBLISHED"].map((status) => `<option value="${status}"${state.filters.runStatus === status ? " selected" : ""}>${status}</option>`).join("")}
        </select>
        <span class="chip">total=${escapeHtml(String(state.runs.length))}</span>
        <span class="chip">filtered=${escapeHtml(String(filteredRuns.length))}</span>
      </div>
      <div class="run-list">
        ${filteredRuns.map((run) => renderRunCard(run)).join("") || renderEmptyState("Run-ов пока нет", "Запусти refresh source или matched sources.")}
      </div>
    </section>
  `;
}

function renderReviewSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Review queue недоступна",
      text: "Queue пустая не потому, что всё хорошо, а потому что backend не вернул governance state.",
      endpoint: API_PATHS.reviewQueue,
    });
  }
  return `
    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">Review queue</div>
          <h3 class="table-card__title">Кандидаты и конфликты</h3>
        </div>
        ${renderStatusChip(state.reviewQueue.length === 0 ? "CLEAN" : `${state.reviewQueue.length} ITEMS`)}
      </div>
      <div class="grid grid--cards">
        ${state.reviewQueue.map((item) => renderReviewCard(item)).join("") || renderEmptyState("Review queue пуста", "Это хороший сигнал: current sources auto-publish без спорных кандидатов.")}
      </div>
    </section>
  `;
}

function renderPublishSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Publish log недоступен",
      text: "Сейчас нельзя понять, что реально published в runtime. Сначала верни связь с control plane.",
      endpoint: API_PATHS.publishEvents,
    });
  }
  const filtered = state.publishEvents.filter((event) => state.filters.publishStatus === "ALL" || event.status === state.filters.publishStatus);
  return `
    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">Serving / runtime</div>
          <h3 class="table-card__title">Publish log</h3>
        </div>
      </div>
      <div class="table-card__toolbar">
        <select class="field__input" data-change="publish-status-filter">
          ${["ALL", "PUBLISHED", "FAILED", "APPROVED", "REJECTED"].map((status) => `<option value="${status}"${state.filters.publishStatus === status ? " selected" : ""}>${status}</option>`).join("")}
        </select>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Event</th>
              <th>Artifact</th>
              <th>Status</th>
              <th>Entity</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            ${filtered.map((event) => renderPublishRow(event)).join("") || `<tr><td colspan="6">${renderEmptyState("Publish log пуст", "После refresh или promote здесь появятся serving/runtime события.")}</td></tr>`}
          </tbody>
        </table>
      </div>
    </section>
  `;
}

function renderRuntimeLabSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Runtime Lab ждёт backend",
      text: "Published known values и aliases нельзя проверять локально без живого catalog backend. Сначала почини control plane, потом смотри runtime.",
      endpoint: API_PATHS.liveValues,
    });
  }
  const snapshot = state.runtimeLab.snapshot || state.branchRuntimeSnapshot;
  const contractRows = isPhonesBranch()
    ? computePhonesContractRows(state.branchSpec, snapshot)
    : [];
  const gaps = summarizeRuntimeGaps(contractRows);
  const scopeLabel = state.runtimeLab.model
    ? "MODEL"
    : state.runtimeLab.brand
      ? "BRAND"
      : "CATEGORY";
  return `
    <section class="lab-card">
      <div class="lab-card__header">
        <div>
          <div class="lab-card__eyebrow">Runtime inspection</div>
          <h3 class="lab-card__title">Published known values & aliases</h3>
        </div>
      </div>
      <div class="split">
        <div>
          <div class="field">
            <span class="field__label">Brand</span>
            <input class="field__input" data-input="runtime-brand" value="${escapeHtml(state.runtimeLab.brand)}" placeholder="Apple, Samsung, Nothing">
          </div>
          <div class="field">
            <span class="field__label">Model</span>
            <input class="field__input" data-input="runtime-model" value="${escapeHtml(state.runtimeLab.model)}" placeholder="iPhone 17 Pro, Galaxy S24+, Phone (2a)">
          </div>
          <div class="field">
            <span class="field__label">Locale</span>
            <input class="field__input" data-input="runtime-locale" value="${escapeHtml(state.runtimeLab.locale)}" placeholder="ru-RU">
          </div>
          <div class="field">
            <span class="field__label">Attribute codes</span>
            <textarea class="field__textarea" data-input="runtime-attributes">${escapeHtml(state.runtimeLab.attributeCodesText)}</textarea>
          </div>
          <div class="lab-card__actions">
            <button class="button button--primary" data-action="load-runtime-lab">Загрузить runtime snapshot</button>
            <button class="button button--ghost" data-action="runtime-use-default-attrs">Phones defaults</button>
          </div>
        </div>

        <div class="detail-card">
          <div class="table-card__eyebrow">Branch contract</div>
          <h3 class="panel__title">Что смотреть в runtime</h3>
          <div class="panel__subtext">
            Если published known values уже есть здесь, а results UI их не показывает, проблема почти наверняка в results policy/render path, а не в governance publish.
          </div>
          <div class="chips" style="margin-top:14px;">
            <span class="chip">scope=${escapeHtml(scopeLabel)}</span>
            ${state.runtimeLab.brand ? `<span class="chip">brand=${escapeHtml(state.runtimeLab.brand)}</span>` : ""}
            ${state.runtimeLab.model ? `<span class="chip">model=${escapeHtml(state.runtimeLab.model)}</span>` : ""}
            <span class="chip">brandOptions=${escapeHtml(String(snapshot?.brandOptions?.length || 0))}</span>
            <span class="chip">modelOptions=${escapeHtml(String(snapshot?.modelOptions?.length || 0))}</span>
            <span class="chip">knownAttrs=${escapeHtml(String(Object.keys(snapshot?.knownValuesByAttributeCode || {}).length))}</span>
            ${isPhonesBranch() ? `<span class="chip">contractHealthy=${escapeHtml(String(contractRows.filter((row) => row.statusKey === "healthy").length))}/${escapeHtml(String(contractRows.length))}</span>` : ""}
          </div>
          ${gaps.length > 0 ? `
            <div class="query-summary" style="margin-top:16px;">
              <div class="field__label">Top runtime gaps</div>
              <div class="token-list">
                ${gaps.map((gap) => `<span class="token token--unresolved">${escapeHtml(gap.label)} · ${escapeHtml(gap.summary)}</span>`).join("")}
              </div>
            </div>
          ` : ""}
          <div class="lab-card__actions" style="margin-top:16px;">
            <button class="button button--ghost" data-action="switch-section" data-section="query-flow">Открыть Query Flow</button>
            ${state.queryFlow.result ? `<button class="button button--ghost" data-action="apply-query-result-to-runtime">Подставить последний query result</button>` : ""}
          </div>
        </div>
      </div>
    </section>

    ${snapshot ? renderRuntimeSnapshot(snapshot) : renderEmptyState("Runtime snapshot ещё не загружен", "Выбери ветку и при необходимости brand/model, затем нажми «Загрузить runtime snapshot».")}
  `;
}

function renderRuntimeSnapshot(snapshot) {
  const knownValues = snapshot.knownValuesByAttributeCode || {};
  const aliases = snapshot.knownValueAliasesByAttributeCode || {};
  const attributeCards = Object.keys(knownValues).sort((left, right) => left.localeCompare(right)).map((attributeCode) => {
    const values = knownValues[attributeCode] || [];
    const aliasMap = aliases[attributeCode] || {};
    return `
      <div class="aliases-card">
        <h4>${escapeHtml(attributeCode)}</h4>
        <div class="chips">
          ${values.map((value) => `<span class="chip">${escapeHtml(value)}</span>`).join("") || `<span class="chip">Нет known values</span>`}
        </div>
        <div class="aliases-table" style="margin-top:12px;">
          ${Object.entries(aliasMap).map(([canonicalValue, aliasList]) => `
            <div>
              <div class="field__label">${escapeHtml(canonicalValue)}</div>
              <div class="chips">${aliasList.map((alias) => `<span class="chip">${escapeHtml(alias)}</span>`).join("") || `<span class="chip">alias-ов нет</span>`}</div>
            </div>
          `).join("") || `<div class="panel__subtext">Для этого атрибута alias-ы не опубликованы.</div>`}
        </div>
      </div>
    `;
  });

  return `
    <section class="runtime-columns">
      <div class="runtime-column">
        <div class="detail-card">
          <div class="table-card__eyebrow">Brand options</div>
          <h3 class="panel__title">Runtime brands</h3>
          <div class="chips">
            ${(snapshot.brandOptions || []).map((option) => `<button class="button button--ghost" data-action="use-runtime-brand" data-runtime-brand="${escapeHtml(option)}">${escapeHtml(option)}</button>`).join("") || `<span class="chip">Нет brand options</span>`}
          </div>
        </div>
        <div class="detail-card">
          <div class="table-card__eyebrow">Model options</div>
          <h3 class="panel__title">Runtime models</h3>
          <div class="chips">
            ${(snapshot.modelOptions || []).map((option) => `<button class="button button--ghost" data-action="use-runtime-model" data-runtime-model="${escapeHtml(option)}">${escapeHtml(option)}</button>`).join("") || `<span class="chip">Нет model options</span>`}
          </div>
        </div>
      </div>
      <div class="runtime-column">
        ${attributeCards.join("")}
      </div>
    </section>
  `;
}

function renderPhonesCoverageSection() {
  const rows = computePhonesContractRows(state.branchSpec, state.branchRuntimeSnapshot);
  const filteredRows = rows.filter((row) => {
    if (state.filters.contractStatus === "ALL") return true;
    if (state.filters.contractStatus === "HEALTHY") return row.statusKey === "healthy";
    return row.statusKey !== "healthy";
  });
  const priorityGroups = computeCoveragePriorityGroups(rows);
  const healthyCount = rows.filter((row) => row.statusKey === "healthy").length;
  const facetReadyCount = rows.filter((row) => row.expectsFacet && row.inSpec && row.facetEnabled && row.knownCount > 0).length;
  const totalKnownValues = rows.reduce((sum, row) => sum + row.knownCount, 0);
  const missingRows = rows.filter((row) => row.statusKey !== "healthy");

  return `
    <section class="table-card">
      <div class="table-card__header">
        <div>
          <div class="table-card__eyebrow">TECH.PHONES contract</div>
          <h3 class="table-card__title">Product master coverage</h3>
        </div>
        ${renderStatusChip(`${healthyCount}/${rows.length} healthy`, healthyCount === rows.length ? "green" : "amber")}
      </div>
      <div class="grid grid--cards" style="margin-bottom:16px;">
        ${priorityGroups.map(renderCoveragePriorityCard).join("")}
      </div>
      <div class="split">
        <div>
          <div class="chips">
            <span class="chip">facet-ready=${escapeHtml(String(facetReadyCount))}/${escapeHtml(String(rows.filter((row) => row.expectsFacet).length))}</span>
            <span class="chip">runtime-values=${escapeHtml(String(totalKnownValues))}</span>
            <span class="chip">missing=${escapeHtml(String(missingRows.length))}</span>
          </div>
          <div class="table-card__toolbar" style="margin-top:14px;">
            <select class="field__input" data-change="contract-status-filter">
              ${["ALL", "GAP", "HEALTHY"].map((status) => `<option value="${status}"${state.filters.contractStatus === status ? " selected" : ""}>${status}</option>`).join("")}
            </select>
            <button class="button button--ghost" data-action="switch-section" data-section="runtime-lab">Открыть Runtime Lab</button>
            <button class="button button--ghost" data-action="switch-section" data-section="query-flow">Открыть Query Flow</button>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Атрибут</th>
                  <th>Статус</th>
                  <th>Facet</th>
                  <th>Known</th>
                  <th>Aliases</th>
                  <th>Usage</th>
                  <th>Next step</th>
                </tr>
              </thead>
              <tbody>
                ${filteredRows.map((row) => renderPhonesCoverageRow(row)).join("")}
              </tbody>
            </table>
          </div>
        </div>

        <div class="detail-card">
          <div class="table-card__eyebrow">Operator focus</div>
          <h3 class="panel__title">Где ветка ещё дырявая</h3>
          <div class="panel__subtext">
            Этот блок показывает must-have phone facts, которые реально влияют на facets, sort, badges и runtime query interpretation.
          </div>
          <div class="recommendation-list" style="margin-top:16px;">
            ${missingRows.slice(0, 6).map((row) => `
              <div class="recommendation-card">
                <strong>${escapeHtml(row.label)}</strong>
                <div class="panel__subtext">${escapeHtml(row.summary)}</div>
              </div>
            `).join("") || `
              <div class="recommendation-card">
                <strong>Контракт выглядит здоровым</strong>
                <div class="panel__subtext">Теперь фокус уже не на schema/runtime gaps, а на реальных query сценариях и market coverage.</div>
              </div>
            `}
          </div>
        </div>
      </div>
    </section>
  `;
}

function buildBranchContractRows(spec) {
  const attrs = (spec?.attributes || []).map((attribute) => ({ ...attribute, kind: "domain" }));
  const systemAttrs = (spec?.systemAttributes || []).map((attribute) => ({ ...attribute, kind: "system" }));
  return [...attrs, ...systemAttrs]
    .sort((left, right) => (left.uiOrder ?? 0) - (right.uiOrder ?? 0) || String(left.code || "").localeCompare(String(right.code || ""), "en"))
    .map((attribute) => ({
      code: attribute.code,
      title: attribute.labels?.ru || attribute.labels?.en || attribute.title || attribute.code,
      kind: attribute.kind,
      dataType: attribute.dataType,
      valueType: attribute.valueType,
      valueSetType: attribute.valueSetType,
      facetEnabled: Boolean(attribute.facetEnabled || attribute.isFacet),
      isIdentity: Boolean(attribute.isIdentity),
      requiredForCategory: Boolean(attribute.requiredForCategory),
      requiredForSearch: Boolean(attribute.requiredForSearch),
      requiredForOffer: Boolean(attribute.requiredForOffer),
      requiredForExpress: Boolean(attribute.requiredForExpress),
      dictionaryRequired: Boolean(attribute.dictionaryRequired),
      enumOnly: Boolean(attribute.enumOnly),
      valueDictCode: attribute.valueDictCode || "",
      widgetHint: attribute.widgetHint || "",
      uiOrder: attribute.uiOrder ?? 0,
      payload: attribute,
    }));
}

function buildGenericContractGaps(rows, spec) {
  const gaps = [];
  const meta = spec?.meta || {};
  if ((meta.identityAttributeCodes || []).length === 0 && rows.filter((row) => row.isIdentity).length === 0) {
    gaps.push({
      title: "Нет identity-атрибутов",
      description: "Ветка не сможет стабильно мапить brand/model/family и будет деградировать в query interpretation.",
    });
  }
  if (rows.filter((row) => row.facetEnabled).length === 0) {
    gaps.push({
      title: "Нет facet-ready атрибутов",
      description: "Связь attr→facet отсутствует. UI не сможет строить полезные фильтры даже при хорошем catalog coverage.",
    });
  }
  const requiredSearchWithoutFacet = rows.filter((row) => row.requiredForSearch && !row.facetEnabled && row.kind !== "system");
  if (requiredSearchWithoutFacet.length > 0) {
    gaps.push({
      title: "Search-required атрибуты не подготовлены к facet/runtime пути",
      description: `Проверь ${requiredSearchWithoutFacet.slice(0, 4).map((row) => row.code).join(", ")}. Они требуются для поиска, но не выглядят facet-ready.`,
    });
  }
  const dictRequiredWithoutDict = rows.filter((row) => row.dictionaryRequired && !row.valueDictCode);
  if (dictRequiredWithoutDict.length > 0) {
    gaps.push({
      title: "Dictionary-required атрибуты без словаря",
      description: `У атрибутов ${dictRequiredWithoutDict.slice(0, 4).map((row) => row.code).join(", ")} не видно value dictionary. Это ломает canonical values и aliases.`,
    });
  }
  return gaps;
}

function renderBranchContractRow(row) {
  const requiredFlags = [
    row.requiredForCategory ? "category" : null,
    row.requiredForSearch ? "search" : null,
    row.requiredForOffer ? "offer" : null,
    row.requiredForExpress ? "express" : null,
  ].filter(Boolean);
  const detailKey = rememberDetail(
    `branch-contract:${state.selectedCategory}:${row.code}`,
    `${row.title} · ${row.code}`,
    row.payload,
    `
      <div class="chips">
        <span class="chip">${escapeHtml(row.kind)}</span>
        ${row.facetEnabled ? renderStatusChip("FACET", "green") : renderStatusChip("NO FACET", "slate")}
        ${row.isIdentity ? renderStatusChip("IDENTITY", "blue") : ""}
        ${requiredFlags.length ? `<span class="chip">required=${escapeHtml(requiredFlags.join(", "))}</span>` : `<span class="chip">required=—</span>`}
      </div>
    `,
  );
  return `
    <tr data-action="open-detail" data-detail-key="${detailKey}">
      <td>
        <strong>${escapeHtml(row.title)}</strong>
        <div class="mono">${escapeHtml(row.code)}</div>
      </td>
      <td>${escapeHtml(row.kind)}</td>
      <td>${row.facetEnabled ? renderStatusChip("ENABLED", "green") : renderStatusChip("OFF", "slate")}</td>
      <td>${row.isIdentity ? renderStatusChip("YES", "blue") : renderStatusChip("NO", "slate")}</td>
      <td>${requiredFlags.length ? escapeHtml(requiredFlags.join(", ")) : "—"}</td>
      <td>
        <div>${escapeHtml([row.dataType, row.valueType].filter(Boolean).join(" / ") || "—")}</div>
        <div class="mono">${escapeHtml(row.valueDictCode || row.widgetHint || "—")}</div>
      </td>
    </tr>
  `;
}

function renderPhonesCoverageRow(row) {
  const detailKey = rememberDetail(
    `contract:${row.code}`,
    `${row.label} · ${row.code}`,
    row.detailPayload,
    `
      <div class="chips">
        ${renderStatusChip(row.statusLabel, row.tone)}
        <span class="chip">${escapeHtml(row.code)}</span>
        <span class="chip">${escapeHtml(row.usage)}</span>
        <span class="chip">known=${escapeHtml(String(row.knownCount))}</span>
        <span class="chip">aliases=${escapeHtml(String(row.aliasCount))}</span>
      </div>
    `,
  );
  return `
    <tr data-action="open-detail" data-detail-key="${detailKey}">
      <td>
        <strong>${escapeHtml(row.label)}</strong>
        <div class="mono">${escapeHtml(row.code)}</div>
      </td>
      <td>${renderStatusChip(row.statusLabel, row.tone)}</td>
      <td>${row.expectsFacet ? (row.facetEnabled ? renderStatusChip("ENABLED", "green") : renderStatusChip("OFF", "amber")) : renderStatusChip("N/A", "slate")}</td>
      <td>${escapeHtml(String(row.knownCount))}</td>
      <td>${escapeHtml(String(row.aliasCount))}</td>
      <td>${escapeHtml(row.usage)}</td>
      <td>
        <div>${escapeHtml(row.summary)}</div>
        <div class="inline-actions" style="margin-top:10px;">
          <button class="button button--ghost" data-action="focus-attribute" data-attribute-code="${escapeHtml(row.code)}">Runtime</button>
        </div>
      </td>
    </tr>
  `;
}

function computeCoveragePriorityGroups(rows) {
  const groups = [
    {
      key: "identity",
      label: "Identity",
      description: "Brand / model identity и must-match факты.",
    },
    {
      key: "primary",
      label: "Primary facets",
      description: "Фасеты, без которых results UI быстро деградирует.",
    },
    {
      key: "secondary",
      label: "Secondary facets",
      description: "Дополнительные, но всё ещё продуктово полезные значения.",
    },
    {
      key: "rich",
      label: "Rich facts",
      description: "Sort / badge / compare signals для сильной phones-ветки.",
    },
  ];
  return groups.map((group) => {
    const items = rows.filter((row) => row.priority === group.key);
    const healthy = items.filter((row) => row.statusKey === "healthy").length;
    const percent = items.length === 0 ? 100 : Math.round((healthy / items.length) * 100);
    const tone = percent >= 100 ? "green" : percent >= 60 ? "amber" : "red";
    const topGap = items.find((row) => row.statusKey !== "healthy");
    return {
      ...group,
      total: items.length,
      healthy,
      percent,
      tone,
      topGap,
    };
  });
}

function renderCoveragePriorityCard(group) {
  return `
    <article class="progress-card">
      <div class="progress-card__header">
        <div>
          <div class="table-card__eyebrow">${escapeHtml(group.label)}</div>
          <h3 class="panel__title">${escapeHtml(String(group.healthy))}/${escapeHtml(String(group.total))}</h3>
        </div>
        ${renderStatusChip(`${group.percent}%`, group.tone)}
      </div>
      <div class="panel__subtext">${escapeHtml(group.description)}</div>
      <div class="progress-bar" aria-hidden="true">
        <div class="progress-bar__fill progress-bar__fill--${escapeHtml(group.tone)}" style="width:${escapeHtml(String(group.percent))}%"></div>
      </div>
      <div class="panel__subtext" style="margin-top:10px;">
        ${group.topGap ? `Ближайший gap: ${group.topGap.label} · ${group.topGap.summary}` : "Группа выглядит здоровой."}
      </div>
    </article>
  `;
}

function renderQueryFlowSection() {
  if (!isBackendHealthy()) {
    return renderControlPlaneUnavailableSection({
      title: "Query Flow сейчас бессмысленен",
      text: "Эта секция опирается на runtime values, effective spec и market check. Пока backend не отвечает, любая диагностика будет фальшивой.",
      endpoint: API_PATHS.readiness,
    });
  }
  const result = state.queryFlow.result;
  const coverageRows = isPhonesBranch()
    ? computePhonesContractRows(state.branchSpec, state.branchRuntimeSnapshot)
    : [];
  const topGap = coverageRows.find((row) => row.statusKey !== "healthy");
  return `
    <section class="lab-card">
      <div class="lab-card__header">
        <div>
          <div class="lab-card__eyebrow">Runtime-backed parsing</div>
          <h3 class="lab-card__title">Query Flow</h3>
        </div>
      </div>
      <div class="query-grid">
        <div class="query-card">
          <div class="field">
            <span class="field__label">Raw query</span>
            <textarea class="field__textarea" data-input="query-flow-raw" placeholder="nothing phone 2a 45w">${escapeHtml(state.queryFlow.rawQuery)}</textarea>
          </div>
          <div class="field__inline">
            <label class="field">
              <span class="field__label">Locale</span>
              <input class="field__input" data-input="query-flow-locale" value="${escapeHtml(state.queryFlow.locale)}" placeholder="ru-RU">
            </label>
            <label class="field">
              <span class="field__label">Attribute codes</span>
              <input class="field__input" data-input="query-flow-attributes" value="${escapeHtml(state.queryFlow.attributeCodesText)}" placeholder="${escapeHtml(DEFAULT_RUNTIME_ATTRIBUTE_CODES.join(", "))}">
            </label>
          </div>
          <div class="lab-card__actions">
            <button class="button button--primary" data-action="run-query-flow">Прогнать query flow</button>
            <button class="button button--ghost" data-action="query-use-default-attrs">Phones defaults</button>
            ${result ? `<button class="button button--ghost" data-action="apply-query-result-to-runtime">Открыть в Runtime Lab</button>` : ""}
          </div>
          <div class="query-samples">
            ${QUERY_FLOW_SAMPLE_QUERIES.map((sample) => `<button class="button button--ghost" data-action="use-query-sample" data-query="${escapeHtml(sample)}">${escapeHtml(sample)}</button>`).join("")}
          </div>
        </div>

        <div class="detail-card">
          <div class="table-card__eyebrow">How to read it</div>
          <h3 class="panel__title">Что даёт этот экран</h3>
          <div class="panel__subtext">
            Он показывает, хватает ли published runtime данных и branch contract, чтобы запрос распознался без плясок вокруг seed и случайных seller-полей.
          </div>
          <div class="chips" style="margin-top:14px;">
            <span class="chip">branch=${escapeHtml(state.selectedCategory)}</span>
            <span class="chip">sources=${escapeHtml(String(state.sources.length))}</span>
            <span class="chip">runtimeAttrs=${escapeHtml(String(Object.keys(state.branchRuntimeSnapshot?.knownValuesByAttributeCode || {}).length))}</span>
          </div>
          ${topGap ? `
            <div class="query-summary" style="margin-top:16px;">
              <div class="field__label">Ближайший gap ветки</div>
              <div class="recommendation-card">
                <strong>${escapeHtml(topGap.label)}</strong>
                <div class="panel__subtext">${escapeHtml(topGap.summary)}</div>
              </div>
            </div>
          ` : ""}
        </div>
      </div>
    </section>

    ${result ? renderQueryFlowResult(result) : renderEmptyState("Query Flow ещё не запускался", "Введи реальный пользовательский запрос и прогоняй его через runtime-backed parse и market check.")}`;
}

function renderQueryFlowResult(result) {
  const diagnosis = result.diagnosis || deriveQueryDiagnosis(result);
  const sourceSuggestion = result.sourceSuggestion || findSourceForBrand(result.inferredBrand);
  const detailKey = rememberDetail(
    `query-flow:${result.rawQuery}:${result.timestamp}`,
    `Query Flow · ${result.rawQuery}`,
    result,
    `
      <div class="chips">
        ${renderStatusChip(diagnosis.label, diagnosis.tone)}
        ${result.inferredBrand ? `<span class="chip">brand=${escapeHtml(result.inferredBrand)}</span>` : ""}
        ${result.inferredModel ? `<span class="chip">model=${escapeHtml(result.inferredModel)}</span>` : ""}
        <span class="chip">matchedAttrs=${escapeHtml(String(result.matchedAttributes.length))}</span>
        <span class="chip">marketTotal=${escapeHtml(String(result.marketCheck?.total ?? 0))}</span>
      </div>
    `,
  );
  const metrics = [
    {
      eyebrow: "Branch",
      value: result.categoryCode,
      status: result.effectiveSpec ? "SPEC READY" : "NO SPEC",
      tone: result.effectiveSpec ? "green" : "red",
      subtext: `runtime attrs=${Object.keys(result.categorySnapshot?.knownValuesByAttributeCode || {}).length}`,
    },
    {
      eyebrow: "Brand / model",
      value: `${result.inferredBrand || "—"} / ${result.inferredModel || "—"}`,
      status: result.inferredModel ? "MATCHED" : "NO MODEL",
      tone: result.inferredModel ? "green" : "amber",
      subtext: "Сначала ищем identity, потом enrich-атрибуты.",
    },
    {
      eyebrow: "Matched attrs",
      value: result.matchedAttributes.length,
      status: result.matchedAttributes.length > 0 ? "RUNTIME BACKED" : "NO ATTRS",
      tone: result.matchedAttributes.length > 0 ? "blue" : "amber",
      subtext: "Найдены через published known values и aliases.",
    },
    {
      eyebrow: "Market check",
      value: result.marketCheck?.total ?? 0,
      status: (result.marketCheck?.total ?? 0) > 0 ? "LIVE OFFERS" : "EMPTY MARKET",
      tone: (result.marketCheck?.total ?? 0) > 0 ? "green" : "amber",
      subtext: "searchWithFacets поверх inferred criteria.",
    },
  ];

  return `
    <section class="diagnosis-card diagnosis-card--${escapeHtml(diagnosis.tone)}">
      <div class="diagnosis-card__header">
        <div>
          <div class="table-card__eyebrow">Primary diagnosis</div>
          <h3 class="panel__title">${escapeHtml(diagnosis.title)}</h3>
        </div>
        ${renderStatusChip(diagnosis.label, diagnosis.tone)}
      </div>
      <div class="panel__subtext">${escapeHtml(diagnosis.description)}</div>
      <div class="chips" style="margin-top:14px;">
        <span class="chip">raw=${escapeHtml(result.rawQuery)}</span>
        ${result.inferredBrand ? `<span class="chip">brand=${escapeHtml(result.inferredBrand)}</span>` : ""}
        ${result.inferredModel ? `<span class="chip">model=${escapeHtml(result.inferredModel)}</span>` : ""}
        ${sourceSuggestion ? `<span class="chip">source=${escapeHtml(sourceSuggestion.displayName || sourceSuggestion.registryCode)}</span>` : ""}
      </div>
      <div class="lab-card__actions" style="margin-top:16px;">
        <button class="button button--ghost" data-action="apply-query-result-to-runtime">Открыть в Runtime Lab</button>
        <button class="button button--ghost" data-action="switch-section" data-section="sources">Открыть Sources</button>
        ${sourceSuggestion ? `<button class="button button--primary" data-action="run-source" data-registry-code="${escapeHtml(sourceSuggestion.registryCode)}">Прогнать ${escapeHtml(result.inferredBrand || "source")}</button>` : ""}
        ${diagnosis.focusAttributeCode ? `<button class="button button--ghost" data-action="focus-attribute" data-attribute-code="${escapeHtml(diagnosis.focusAttributeCode)}">Проверить ${escapeHtml(diagnosis.focusAttributeCode)} в Runtime</button>` : ""}
      </div>
    </section>

    <section class="grid grid--metrics">
      ${metrics.map(renderMetricCard).join("")}
    </section>

    <section class="query-grid">
      <div class="detail-card">
        <div class="table-card__eyebrow">Inference</div>
        <h3 class="panel__title">Что распозналось</h3>
        <div class="query-summary">
          <div class="field__label">Matched tokens</div>
          <div class="token-list">
            ${(result.consumedPhrases || []).map((token) => `<span class="token token--matched">${escapeHtml(token)}</span>`).join("") || `<span class="token">Нет поглощённых токенов</span>`}
          </div>
        </div>
        <div class="query-summary" style="margin-top:16px;">
          <div class="field__label">Unresolved tail</div>
          <div class="token-list">
            ${(result.unresolvedTokens || []).map((token) => `<span class="token token--unresolved">${escapeHtml(token)}</span>`).join("") || `<span class="token token--resolved">Хвоста нет</span>`}
          </div>
        </div>
        <div class="query-summary" style="margin-top:16px;">
          <div class="field__label">Matched attributes</div>
          <div class="contract-grid">
            ${result.matchedAttributes.map((item) => `
              <div class="contract-card">
                <div class="contract-card__header">
                  <strong>${escapeHtml(item.label)}</strong>
                  ${renderStatusChip(item.scopeStatus.toUpperCase(), item.scopeStatus === "scoped" ? "green" : "amber")}
                </div>
                <div class="panel__subtext">${escapeHtml(item.canonicalValue)}</div>
                <div class="chips" style="margin-top:10px;">
                  <span class="chip">${escapeHtml(item.code)}</span>
                  <span class="chip">${escapeHtml(item.usage)}</span>
                  <span class="chip">match=${escapeHtml(item.matchedVariant)}</span>
                </div>
              </div>
            `).join("") || renderEmptyState("Ничего не распознано", "Проверь aliases, runtime known values или сам branch contract.")}
          </div>
        </div>
      </div>

      <div class="detail-card">
        <div class="table-card__eyebrow">Operator recommendation</div>
        <h3 class="panel__title">Что делать дальше</h3>
        <div class="recommendation-list">
          ${(result.recommendations || []).map((item) => `
            <div class="recommendation-card">
              <strong>${escapeHtml(item.title)}</strong>
              <div class="panel__subtext">${escapeHtml(item.description)}</div>
            </div>
          `).join("")}
        </div>
        <div class="lab-card__actions" style="margin-top:16px;">
          <button class="button button--ghost" data-action="open-detail" data-detail-key="${detailKey}">Inspect JSON</button>
        </div>
      </div>
    </section>
  `;
}

function isPhonesBranch(categoryCode = state.selectedCategory) {
  return String(categoryCode || "").toUpperCase().startsWith("TECH.PHONES");
}

function getEffectiveSpecAttributes(spec) {
  return [...(spec?.attributes || []), ...(spec?.systemAttributes || [])];
}

function computePhonesContractRows(spec, snapshot) {
  const attributes = getEffectiveSpecAttributes(spec);
  const specByCode = new Map(attributes.map((attribute) => [attribute.code, attribute]));
  const knownValues = snapshot?.knownValuesByAttributeCode || {};
  const knownAliases = snapshot?.knownValueAliasesByAttributeCode || {};

  return PHONES_ATTRIBUTE_PLAYBOOK.map((item) => {
    const attributeSpec = specByCode.get(item.code);
    const valueList = knownValues[item.code] || [];
    const aliasMap = knownAliases[item.code] || {};
    const aliasCount = Object.values(aliasMap).reduce((sum, aliases) => sum + (aliases?.length || 0), 0);
    const expectsFacet = item.usage.includes("facet");
    const inSpec = Boolean(attributeSpec);
    const facetEnabled = Boolean(attributeSpec?.isFacet || attributeSpec?.facetEnabled);
    let statusKey = "healthy";
    let statusLabel = "HEALTHY";
    let tone = "green";
    let summary = "Контракт и runtime выглядят консистентно.";

    if (!inSpec) {
      statusKey = "missing_contract";
      statusLabel = "MISSING_CONTRACT";
      tone = "red";
      summary = "Атрибут не входит в effective spec ветки.";
    } else if (expectsFacet && !facetEnabled) {
      statusKey = "facet_off";
      statusLabel = "FACET_OFF";
      tone = "amber";
      summary = "Атрибут есть в контракте, но не отмечен как facet-ready.";
    } else if (valueList.length === 0) {
      statusKey = "missing_runtime";
      statusLabel = "NO_RUNTIME_VALUES";
      tone = "red";
      summary = "Published runtime universe пуст. Проверь refresh/publish coverage.";
    } else if ((item.code === "model" || item.code === "color" || item.code === "memory_gb") && aliasCount === 0) {
      statusKey = "weak_aliases";
      statusLabel = "THIN_ALIASES";
      tone = "amber";
      summary = "Known values опубликованы, но alias coverage всё ещё тонкий.";
    }

    return {
      ...item,
      inSpec,
      facetEnabled,
      expectsFacet,
      knownCount: valueList.length,
      aliasCount,
      statusKey,
      statusLabel,
      tone,
      summary,
      detailPayload: {
        playbook: item,
        attributeSpec,
        runtimeValues: valueList,
        runtimeAliases: aliasMap,
      },
    };
  });
}

function summarizeRuntimeGaps(contractRows) {
  const gaps = [];
  const missingContractRows = contractRows.filter((row) => row.statusKey === "missing_contract");
  if (missingContractRows.length > 0) {
    const labels = missingContractRows.map((row) => row.label);
    gaps.push({
      label: missingContractRows.length > 1 ? "Richer phone facts" : labels[0],
      summary: missingContractRows.length > 1
        ? `Runtime contract отстаёт: ${labels.join(", ")} ещё не вошли в effective spec. Перезапусти backend и нажми Rebuild branch.`
        : missingContractRows[0].summary,
    });
  }
  const firstMissingRuntimeModel = contractRows.find((row) => row.code === "model" && row.statusKey === "missing_runtime");
  if (firstMissingRuntimeModel) {
    gaps.push({
      label: firstMissingRuntimeModel.label,
      summary: firstMissingRuntimeModel.summary,
    });
  }
  const firstWeakAliases = contractRows.find((row) => row.statusKey === "weak_aliases");
  if (firstWeakAliases) {
    gaps.push({
      label: firstWeakAliases.label,
      summary: firstWeakAliases.summary,
    });
  }
  const facetOffRows = contractRows.filter((row) => row.statusKey === "facet_off");
  facetOffRows.slice(0, 2).forEach((row) => {
    gaps.push({
      label: row.label,
      summary: row.summary,
    });
  });
  return gaps.slice(0, 6);
}

async function runQueryFlow() {
  const rawQuery = state.queryFlow.rawQuery.trim();
  if (!rawQuery) {
    showToast("Введи query для runtime-backed разбора.", "danger");
    return;
  }

  const locale = state.queryFlow.locale || "ru-RU";
  const attributeCodes = parseAttributeCodes(state.queryFlow.attributeCodesText);
  const categorySnapshot = await apiGet(API_PATHS.liveValues, {
    categoryCode: state.selectedCategory,
    locale,
    attributeCode: attributeCodes,
  });
  state.branchRuntimeSnapshot = categorySnapshot;
  if (!state.runtimeLab.brand && !state.runtimeLab.model) {
    state.runtimeLab.snapshot = categorySnapshot;
  }
  const brandCandidates = collectBrandCandidates(categorySnapshot, state.sources);
  const brandMatch = findBestCandidateMatch(rawQuery, brandCandidates);
  let inferredBrand = brandMatch?.canonical || "";
  let inferredBrandScope = brandMatch?.apiBrand || inferredBrand;
  let inferredMarketBrand = brandMatch?.marketBrand || inferredBrand;
  const modelMatch = matchAttributeFromSnapshot("model", rawQuery, categorySnapshot, "facet");
  let inferredModel = modelMatch?.canonicalValue || "";
  if (!inferredBrandScope && inferredModel) {
    const inferredBrandMatch = inferBrandForModel(inferredModel, categorySnapshot);
    inferredBrand = inferredBrandMatch?.canonical || "";
    inferredBrandScope = inferredBrandMatch?.apiBrand || inferredBrand;
    inferredMarketBrand = inferredBrandMatch?.marketBrand || inferredBrand;
  }

  const detailedSnapshot = await apiGet(API_PATHS.liveValues, {
    categoryCode: state.selectedCategory,
    brand: inferredBrandScope || undefined,
    model: inferredModel || undefined,
    locale,
    attributeCode: attributeCodes,
  });
  const effectiveSpec = await apiGet(API_PATHS.effectiveSpec(state.selectedCategory), {
    brand: inferredBrandScope || undefined,
    model: inferredModel || undefined,
  });
  const matchedAttributes = matchQueryAttributes(rawQuery, categorySnapshot, detailedSnapshot, effectiveSpec)
    .filter((item) => item.code !== "model");
  const consumedPhrases = [
    brandMatch?.matchedVariant,
    modelMatch?.matchedVariant,
    ...matchedAttributes.map((item) => item.matchedVariant),
  ].filter(Boolean);
  const unresolvedTokens = computeUnresolvedTokens(rawQuery, consumedPhrases);
  const marketCheck = await runMarketCheck({
    categoryCode: state.selectedCategory,
    locale,
    brand: inferredMarketBrand || inferredBrand,
    model: inferredModel,
    matchedAttributes,
  });
  const sourceSuggestion = findSourceForBrand(inferredBrandScope || inferredBrand);
  const diagnosis = deriveQueryDiagnosis({
    inferredBrand,
    inferredModel,
    matchedAttributes,
    unresolvedTokens,
    effectiveSpec,
    marketCheck,
  });

  state.queryFlow.result = {
    timestamp: Date.now(),
    rawQuery,
    categoryCode: state.selectedCategory,
    inferredBrand,
    inferredBrandScope,
    inferredMarketBrand,
    inferredModel,
    brandMatch,
    modelMatch,
    matchedAttributes,
    consumedPhrases,
    unresolvedTokens,
    categorySnapshot,
    detailedSnapshot,
    effectiveSpec,
    marketCheck,
    sourceSuggestion,
    diagnosis,
    recommendations: buildQueryRecommendations({
      inferredBrand,
      inferredModel,
      matchedAttributes,
      unresolvedTokens,
      effectiveSpec,
      marketCheck,
      diagnosis,
      sourceSuggestion,
    }),
  };
  render();
  showToast("Query flow рассчитан.", "success");
}

function parseAttributeCodes(text) {
  return String(text || "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function collectBrandCandidates(snapshot, sources) {
  const candidates = new Map();
  const brandOptions = Array.isArray(snapshot?.brandOptions) ? snapshot.brandOptions : [];
  sources.forEach((source) => {
    const metadata = source?.metadata || {};
    const apiBrand = String(
      metadata.officialBrandCode ||
      metadata.brandCode ||
      metadata.brand ||
      deriveBrandLabelFromSource(source) ||
      "",
    ).trim();
    if (!apiBrand) return;
    addBrandCandidate(candidates, {
      canonical: pickBrandDisplayLabel(apiBrand, brandOptions, source),
      apiBrand,
      marketBrand: metadata.brand || deriveBrandLabelFromSource(source) || humanizeBrandCode(apiBrand) || apiBrand,
      variants: [
        metadata.brand,
        apiBrand,
        humanizeBrandCode(metadata.brandCode),
        humanizeBrandCode(metadata.officialBrandCode),
        deriveBrandLabelFromSource(source),
        source?.displayName,
        source?.sourceCode,
        source?.registryCode,
        ...getBrandHintVariants(apiBrand),
      ],
    });
  });
  brandOptions.forEach((brand) => {
    const normalizedBrand = normalizeMatchText(brand);
    if (!normalizedBrand) return;
    const existing = Array.from(candidates.values()).find((candidate) =>
      Array.from(candidate.variants || []).some((variant) => normalizeMatchText(variant) === normalizedBrand),
    );
    if (existing) {
      existing.canonical = brand;
      existing.variants.add(brand);
      return;
    }
    addBrandCandidate(candidates, {
      canonical: brand,
      apiBrand: brand,
      marketBrand: brand,
      variants: [brand],
    });
  });
  return Array.from(candidates.values());
}

function matchQueryAttributes(rawQuery, categorySnapshot, detailedSnapshot, effectiveSpec) {
  const specByCode = new Map(getEffectiveSpecAttributes(effectiveSpec).map((attribute) => [attribute.code, attribute]));
  return PHONES_ATTRIBUTE_PLAYBOOK
    .map((item) => {
      const categoryMatch = matchAttributeFromSnapshot(item.code, rawQuery, categorySnapshot, item.usage);
      if (!categoryMatch) return null;
      const scopedValues = detailedSnapshot?.knownValuesByAttributeCode?.[item.code] || [];
      const scopeStatus = scopedValues.includes(categoryMatch.canonicalValue) ? "scoped" : "category-only";
      return {
        ...categoryMatch,
        label: item.label,
        usage: item.usage,
        scopeStatus,
        inSpec: specByCode.has(item.code),
      };
    })
    .filter(Boolean);
}

function matchAttributeFromSnapshot(attributeCode, rawQuery, snapshot, usage) {
  const aliasMap = snapshot?.knownValueAliasesByAttributeCode?.[attributeCode] || {};
  const directValues = snapshot?.knownValuesByAttributeCode?.[attributeCode] || [];
  const candidates = new Map();
  Object.entries(aliasMap).forEach(([canonicalValue, aliases]) => {
    addCandidateVariants(candidates, canonicalValue, [canonicalValue, ...(aliases || [])]);
  });
  directValues.forEach((value) => addCandidateVariants(candidates, value, [value]));
  if (attributeCode === "model") {
    (snapshot?.modelOptions || []).forEach((value) => addCandidateVariants(candidates, value, [value]));
  }
  const matched = findBestCandidateMatch(rawQuery, Array.from(candidates.values()));
  if (!matched) return null;
  return {
    code: attributeCode,
    canonicalValue: matched.canonical,
    matchedVariant: matched.matchedVariant,
    usage,
  };
}

function addCandidateVariants(targetMap, canonical, variants) {
  const key = String(canonical || "").trim();
  if (!key) return;
  const existing = targetMap.get(key) || { canonical: key, variants: new Set() };
  variants
    .map((variant) => String(variant || "").trim())
    .filter(Boolean)
    .forEach((variant) => existing.variants.add(variant));
  targetMap.set(key, existing);
}

function addBrandCandidate(targetMap, candidate) {
  const apiBrand = String(candidate?.apiBrand || candidate?.canonical || "").trim();
  if (!apiBrand) return;
  const existing = targetMap.get(apiBrand) || {
    canonical: String(candidate?.canonical || apiBrand).trim(),
    apiBrand,
    marketBrand: String(candidate?.marketBrand || candidate?.canonical || apiBrand).trim(),
    variants: new Set(),
  };
  if (candidate?.canonical) {
    existing.canonical = String(candidate.canonical).trim() || existing.canonical;
  }
  if (candidate?.marketBrand) {
    existing.marketBrand = String(candidate.marketBrand).trim() || existing.marketBrand;
  }
  (candidate?.variants || [])
    .map((variant) => String(variant || "").trim())
    .filter(Boolean)
    .forEach((variant) => existing.variants.add(variant));
  if (existing.canonical) existing.variants.add(existing.canonical);
  if (existing.apiBrand) existing.variants.add(existing.apiBrand);
  if (existing.marketBrand) existing.variants.add(existing.marketBrand);
  targetMap.set(apiBrand, existing);
}

function findBestCandidateMatch(rawQuery, candidates) {
  const queryTokens = tokenizeMatchText(rawQuery);
  let best = null;
  candidates.forEach((candidate) => {
    Array.from(candidate.variants || []).forEach((variant) => {
      const normalized = normalizeMatchText(variant);
      const candidateTokens = tokenizeMatchText(variant);
      const compact = candidateTokens.join("");
      const matched = (
        (normalized && hasTokenWindowMatch(queryTokens, candidateTokens)) ||
        (compact && hasCompactWindowMatch(queryTokens, compact))
      );
      if (!matched) return;
      if (!best || normalized.length > best.score) {
        best = {
          canonical: candidate.canonical,
          apiBrand: candidate.apiBrand,
          marketBrand: candidate.marketBrand,
          matchedVariant: variant,
          score: normalized.length,
        };
      }
    });
  });
  return best;
}

function tokenizeMatchText(value) {
  const normalized = normalizeMatchText(value);
  return normalized ? normalized.split(" ").filter(Boolean) : [];
}

function normalizeMatchText(value) {
  return String(value || "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}+]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function compactMatchText(value) {
  return normalizeMatchText(value).replace(/\s+/g, "");
}

function hasTokenWindowMatch(queryTokens, candidateTokens) {
  if (!candidateTokens.length || candidateTokens.length > queryTokens.length) return false;
  for (let index = 0; index <= queryTokens.length - candidateTokens.length; index += 1) {
    let matched = true;
    for (let offset = 0; offset < candidateTokens.length; offset += 1) {
      if (queryTokens[index + offset] !== candidateTokens[offset]) {
        matched = false;
        break;
      }
    }
    if (matched) return true;
  }
  return false;
}

function hasCompactWindowMatch(queryTokens, candidateCompact) {
  if (!candidateCompact || !queryTokens.length) return false;
  for (let start = 0; start < queryTokens.length; start += 1) {
    let compact = "";
    for (let end = start; end < queryTokens.length; end += 1) {
      compact += queryTokens[end];
      if (compact === candidateCompact) return true;
      if (compact.length >= candidateCompact.length) break;
    }
  }
  return false;
}

function getBrandHintVariants(brandCode) {
  const normalizedCode = String(brandCode || "").trim().toUpperCase();
  const hints = PHONE_BRAND_HINTS_BY_CODE[normalizedCode] || [];
  return Array.from(new Set([
    ...hints,
    brandCode,
    humanizeBrandCode(brandCode),
  ].map((item) => String(item || "").trim()).filter(Boolean)));
}

function pickBrandDisplayLabel(apiBrand, brandOptions, source) {
  const hintSet = new Set(getBrandHintVariants(apiBrand).map((item) => normalizeMatchText(item)).filter(Boolean));
  const matchedOption = (brandOptions || []).find((option) => hintSet.has(normalizeMatchText(option)));
  return matchedOption || deriveBrandLabelFromSource(source) || humanizeBrandCode(apiBrand) || String(apiBrand || "").trim();
}

function humanizeBrandCode(value) {
  const normalized = String(value || "").trim();
  if (!normalized) return "";
  return normalized
    .toLowerCase()
    .split(/[_\s]+/)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
    .join(" ");
}

function deriveBrandLabelFromSource(source) {
  const displayName = String(source?.displayName || "").trim();
  if (displayName) {
    return displayName
      .replace(/\s+Official\s+Phones\s+Refresh$/i, "")
      .replace(/\s+Official\s+Phones$/i, "")
      .trim();
  }
  return humanizeBrandCode(source?.metadata?.officialBrandCode || source?.metadata?.brandCode || source?.sourceCode);
}

function computeUnresolvedTokens(rawQuery, consumedPhrases) {
  const counts = new Map();
  consumedPhrases
    .flatMap((phrase) => normalizeMatchText(phrase).split(" ").filter(Boolean))
    .forEach((token) => counts.set(token, (counts.get(token) || 0) + 1));
  return normalizeMatchText(rawQuery)
    .split(" ")
    .filter(Boolean)
    .filter((token) => {
      const current = counts.get(token) || 0;
      if (current <= 0) return true;
      counts.set(token, current - 1);
      return false;
    });
}

function inferBrandForModel(model, categorySnapshot) {
  return findBestCandidateMatch(
    model,
    collectBrandCandidates(categorySnapshot || state.branchRuntimeSnapshot, state.sources),
  );
}

function findSourceForBrand(brand) {
  const brandKey = normalizeMatchText(brand);
  if (!brandKey) return null;
  return state.sources.find((source) => {
    const variants = [
      source.metadata?.brand,
      source.metadata?.brandCode,
      source.metadata?.officialBrandCode,
      humanizeBrandCode(source.metadata?.brandCode),
      humanizeBrandCode(source.metadata?.officialBrandCode),
      deriveBrandLabelFromSource(source),
      ...getBrandHintVariants(source.metadata?.officialBrandCode || source.metadata?.brandCode || source.metadata?.brand),
      source.sourceCode,
      source.displayName,
      source.registryCode,
    ].filter(Boolean);
    return variants.some((variant) => {
      const normalized = normalizeMatchText(variant);
      return normalized && (normalized.includes(brandKey) || brandKey.includes(normalized));
    });
  }) || null;
}

function deriveQueryDiagnosis({ inferredBrand, inferredModel, matchedAttributes, unresolvedTokens, effectiveSpec, marketCheck }) {
  if (marketCheck?.error) {
    return {
      code: "market_check_failed",
      label: "FAILED",
      tone: "red",
      title: "Проверка рынка упала",
      description: "Сначала почини market-check/search path. Пока он падает, parser/runtime нельзя считать надёжно проверенными.",
      focusAttributeCode: null,
    };
  }
  if (!inferredBrand && !inferredModel) {
    return {
      code: "identity_missing",
      label: "IDENTITY GAP",
      tone: "red",
      title: "Запрос не распознал brand/model",
      description: "Главный риск сейчас не в офферах, а в model/brand coverage: нужны aliases, published known models или свежий source refresh.",
      focusAttributeCode: "model",
    };
  }
  if (inferredBrand && !inferredModel) {
    return {
      code: "model_missing",
      label: "MODEL GAP",
      tone: "red",
      title: "Бренд найден, но модель не подтверждена",
      description: "Проверь published model universe, model aliases и свежесть official source конкретного бренда.",
      focusAttributeCode: "model",
    };
  }
  if (effectiveSpec && matchedAttributes.some((item) => !item.inSpec)) {
    const offending = matchedAttributes.find((item) => !item.inSpec);
    return {
      code: "contract_gap",
      label: "CONTRACT GAP",
      tone: "amber",
      title: "Runtime нашёл значение вне branch contract",
      description: "Значение уже известно runtime, но веточный контракт его не использует. Это сигнал к правке profiles/facet rules, а не sources.",
      focusAttributeCode: offending?.code || null,
    };
  }
  if (matchedAttributes.some((item) => item.scopeStatus !== "scoped")) {
    const scopedGap = matchedAttributes.find((item) => item.scopeStatus !== "scoped");
    return {
      code: "scoped_runtime_gap",
      label: "SCOPED GAP",
      tone: "amber",
      title: "Атрибут распознан только на category scope",
      description: "Запрос уже знает модель, но точный brand/model runtime universe ещё не опубликован или слишком тонкий.",
      focusAttributeCode: scopedGap?.code || null,
    };
  }
  if (unresolvedTokens.length > 0) {
    return {
      code: "alias_tail_gap",
      label: "TAIL GAP",
      tone: "amber",
      title: "У запроса остался необъяснённый хвост",
      description: "Обычно это missing alias, новый canonical value или parser normalization gap по хвостовому атрибуту.",
      focusAttributeCode: matchedAttributes[0]?.code || "model",
    };
  }
  if ((marketCheck?.total ?? 0) === 0) {
    return {
      code: "market_empty",
      label: "EMPTY MARKET",
      tone: "amber",
      title: "Каталог распознал запрос, но рынок пуст",
      description: "Проблема уже не в parser/runtime. Смотри live offers, seller coverage и слишком жёсткие filters.",
      focusAttributeCode: matchedAttributes[0]?.code || null,
    };
  }
  return {
    code: "healthy",
    label: "HEALTHY",
    tone: "green",
    title: "Запрос проходит через контур без явных дыр",
    description: "Identity, runtime-known values и market check выглядят согласованно. Осталось полировать UX и product heuristics.",
    focusAttributeCode: matchedAttributes[0]?.code || null,
  };
}

async function runMarketCheck({ categoryCode, locale, brand, model, matchedAttributes }) {
  const attributeFilters = {};
  matchedAttributes.forEach((item) => {
    attributeFilters[item.code] = {
      op: "EQ",
      value: coerceTypedValue(item.canonicalValue),
    };
  });
  const body = {
    criteria: {
      brand: brand || null,
      model: model || null,
      categoryCode,
      attributeFilters,
      userLanguage: locale,
      limit: 3,
      sort: "RANK",
    },
    attributeFacetKeys: matchedAttributes.map((item) => item.code),
  };
  try {
    return await apiPostJson(API_PATHS.searchWithFacets, body);
  } catch (error) {
    return {
      total: 0,
      facets: { attributes: {} },
      error: error.message || "Market check failed",
    };
  }
}

function coerceTypedValue(value) {
  if (typeof value === "boolean" || typeof value === "number") return value;
  const raw = String(value || "").trim();
  if (/^(true|yes|да)$/i.test(raw)) return true;
  if (/^(false|no|нет)$/i.test(raw)) return false;
  if (/^-?\d+(\.\d+)?$/.test(raw)) return Number(raw);
  return raw;
}

function buildQueryRecommendations({ inferredBrand, inferredModel, matchedAttributes, unresolvedTokens, effectiveSpec, marketCheck, diagnosis, sourceSuggestion }) {
  const recommendations = [];
  if (diagnosis) {
    recommendations.push({
      title: `Primary diagnosis: ${diagnosis.title}`,
      description: diagnosis.description,
    });
  }
  if (marketCheck?.error) {
    recommendations.push({
      title: "Market check завершился ошибкой",
      description: marketCheck.error,
    });
  }
  if (!inferredModel) {
    recommendations.push({
      title: "Проверь model aliases",
      description: "Бренд или модель не распознались. Обычно это означает missing alias coverage или отсутствие runtime-known model values.",
    });
  }
  if (matchedAttributes.some((item) => item.scopeStatus !== "scoped")) {
    recommendations.push({
      title: "Есть category-only значения",
      description: "Часть атрибутов распозналась только на category scope. Проверь brand/model scoped publish и official coverage.",
    });
  }
  if (unresolvedTokens.length > 0) {
    recommendations.push({
      title: "Остался необъяснённый хвост",
      description: `Неразобранные токены: ${unresolvedTokens.join(", ")}. Ищи missing alias, parser gap или новый value.`,
    });
  }
  if (effectiveSpec && matchedAttributes.some((item) => !item.inSpec)) {
    recommendations.push({
      title: "Атрибут вне branch contract",
      description: "Runtime value нашёлся, но effective spec его не считает частью контракта ветки. Проверь profiles/facet rules.",
    });
  }
  if ((marketCheck?.total ?? 0) === 0 && (inferredBrand || inferredModel || matchedAttributes.length > 0)) {
    recommendations.push({
      title: "Runtime распознал запрос, но рынок пуст",
      description: "Это уже не parser issue. Проверь live offers, seller coverage или слишком жёсткие filters.",
    });
  }
  if (sourceSuggestion && inferredBrand) {
    recommendations.push({
      title: `Есть прямой source для ${inferredBrand}`,
      description: `Если сомневаешься в model/value coverage, прогони ${sourceSuggestion.displayName || sourceSuggestion.registryCode} и смотри refresh run вместо ручного гадания.`,
    });
  }
  if (recommendations.length === 0) {
    recommendations.push({
      title: "Контур выглядит здоровым",
      description: "Query разбирается, runtime поддерживает known universe, market check не противоречит ожиданиям.",
    });
  }
  return recommendations;
}

function buildRunMetrics(runs, filteredRuns, publishEvents) {
  const operational = collectOperationalSnapshot(runs, publishEvents);
  const publishedRuns = runs.filter((run) => run.publishStatus === "PUBLISHED");
  const latestRun = operational.latestRun;
  const activeBlockers = operational.activeFailedRuns.length + operational.activeStandalonePublishFailures.length;
  const historicalFailures = operational.historicalFailedRuns.length + operational.historicalStandalonePublishFailures.length;
  const latestRunIsActiveFailure = latestRun && operational.activeFailure?.kind === "run" && operational.activeFailure.payload?.id === latestRun.id;
  const latestRunHistoricalFailure = latestRun && isRunFailure(latestRun) && !latestRunIsActiveFailure;
  return [
    {
      eyebrow: "Всего run-ов",
      value: runs.length,
      status: `${filteredRuns.length} visible`,
      tone: "blue",
      subtext: `Фильтр сейчас: ${state.filters.runStatus}.`,
    },
    {
      eyebrow: "Ошибки / блокеры",
      value: activeBlockers,
      status: activeBlockers > 0 ? "ACTION REQUIRED" : historicalFailures > 0 ? "HISTORY" : "CLEAN",
      tone: activeBlockers > 0 ? "red" : historicalFailures > 0 ? "amber" : "green",
      subtext: operational.activeFailure
        ? `${operationalEntryLabel(operational.activeFailure)} · ${operationalEntrySummary(operational.activeFailure)}`
        : historicalFailures > 0
          ? `Исторические failed: ${historicalFailures}. Последний успешный publish/refresh уже новее этих ошибок.`
          : "Последние refresh и publish без красных статусов.",
    },
    {
      eyebrow: "Published",
      value: publishedRuns.length,
      status: operational.latestPublish?.status || (publishedRuns.length > 0 ? "PUBLISHED" : "WAITING"),
      tone: (operational.latestPublish?.status || (publishedRuns.length > 0 ? "PUBLISHED" : "WAITING")) === "PUBLISHED" ? "green" : "amber",
      subtext: operational.latestPublish
        ? `Последний publish: ${summarizePublish(operational.latestPublish)}`
        : "Runs со статусом publish=PUBLISHED в текущем snapshot.",
    },
    {
      eyebrow: "Последний запуск",
      value: latestRun ? formatDateTime(latestRun.startedAt) : "—",
      status: latestRunHistoricalFailure ? "HISTORY" : latestRun?.status || "NO RUNS",
      tone: latestRunIsActiveFailure
        ? "red"
        : latestRunHistoricalFailure
          ? "amber"
        : latestRun?.publishStatus === "PUBLISHED"
          ? "green"
          : latestRun
            ? "blue"
            : "slate",
      subtext: latestRun
        ? `${runDisplayTitle(latestRun)} · ${summarizeRun(latestRun)}${latestRunHistoricalFailure ? " · Есть более новый successful publish/rebuild." : ""}`
        : "После refresh source или matched sources здесь появится свежий статус.",
    },
  ];
}

function renderMetricCard(metric) {
  return `
    <article class="metric">
      <div class="metric__row">
        <span class="metric__eyebrow">${escapeHtml(metric.eyebrow)}</span>
        ${renderStatusChip(metric.status, metric.tone)}
      </div>
      <div class="metric__value">${escapeHtml(String(metric.value))}</div>
      <div class="metric__subtext">${escapeHtml(metric.subtext)}</div>
    </article>
  `;
}

function renderCompactMetricCard(metric) {
  return `
    <article class="metric metric--compact">
      <div class="metric__row">
        <span class="metric__eyebrow">${escapeHtml(metric.eyebrow)}</span>
        ${renderStatusChip(metric.status, metric.tone)}
      </div>
      <div class="metric__value">${escapeHtml(String(metric.value))}</div>
      <div class="metric__subtext">${escapeHtml(metric.subtext)}</div>
    </article>
  `;
}

function renderBranchRow(item) {
  const detailKey = rememberDetail(`branch:${item.category.code}`, categoryDisplayName(item.category) || item.category.code, item, `
    <div class="chips">
      ${renderStatusChip(item.readiness)}
      ${renderStatusChip(item.operationalReadiness)}
      ${renderStatusChip(item.editorialReadiness)}
      <span class="chip">completeness=${item.completenessGatePassed ? "passed" : "failed"}</span>
    </div>
  `);
  return `
    <tr data-action="select-category" data-category-code="${escapeHtml(item.category.code)}" data-detail-key="${detailKey}">
      <td>
        <strong>${escapeHtml(categoryDisplayName(item.category) || item.category.code)}</strong>
        <div class="mono">${escapeHtml(item.category.code)}</div>
      </td>
      <td>${renderStatusChip(item.readiness)}</td>
      <td>${renderStatusChip(item.editorialReadiness)}</td>
      <td>${renderStatusChip(item.operationalReadiness)}</td>
      <td>${item.completenessGatePassed ? renderStatusChip("PASSED", "green") : renderStatusChip("FAILED", "red")}</td>
      <td>${escapeHtml(String((item.blockingIssues || []).length))}</td>
    </tr>
  `;
}

function categoryDisplayName(category) {
  return category?.displayName
    || category?.title?.ru
    || category?.title?.en
    || category?.code
    || "";
}

function renderSourceCard(source) {
  const detailKey = rememberDetail(`source:${source.registryCode}`, source.displayName, source, `
    <div class="chips">
      ${renderStatusChip(source.enabled ? "ENABLED" : "DISABLED")}
      ${renderStatusChip(source.autoPublish ? "AUTO PUBLISH" : "MANUAL")}
      <span class="chip">${escapeHtml(source.connectorType)}</span>
      <span class="chip">${escapeHtml(source.tier)}</span>
    </div>
  `);
  const brand = source.metadata?.brand || source.metadata?.brandCode || "—";
  const endpointCount = source.metadata?.endpointCount || source.metadata?.endpoints || "—";
  return `
    <article class="source-card" data-detail-key="${detailKey}">
      <div class="source-card__header">
        <div>
          <div class="source-card__eyebrow">${escapeHtml(source.registryCode)}</div>
          <h3 class="source-card__title">${escapeHtml(source.displayName)}</h3>
        </div>
        ${renderStatusChip(source.enabled ? "ENABLED" : "DISABLED", source.enabled ? "green" : "slate")}
      </div>
      <div class="chips">
        <span class="chip">brand=${escapeHtml(brand)}</span>
        <span class="chip">endpoints=${escapeHtml(String(endpointCount))}</span>
        <span class="chip">auto=${source.autoPublish ? "yes" : "no"}</span>
      </div>
      <div class="source-card__subtext">${escapeHtml(source.sourceUri || source.externalRef || "Source URI not published")}</div>
      <div class="source-card__actions" style="margin-top:14px;">
        <button class="button button--primary" data-action="run-source" data-registry-code="${escapeHtml(source.registryCode)}">Run source</button>
        <button class="button button--ghost" data-action="open-detail" data-detail-key="${detailKey}">Inspect</button>
      </div>
    </article>
  `;
}

function runDisplayTitle(run) {
  return run?.metadata?.officialSourceCode || run?.registryCode || `Run #${run?.id || "—"}`;
}

function renderRunCard(run) {
  const diagnosis = diagnoseOperationalError(run.errorMessage);
  const detailKey = rememberDetail(`run:${run.id}`, `Run #${run.id}`, run, `
    <div class="chips">
      ${renderStatusChip(run.status)}
      ${run.publishStatus ? renderStatusChip(run.publishStatus) : ""}
      <span class="chip">${escapeHtml(run.registryCode)}</span>
    </div>
    ${diagnosis ? renderOperationalDiagnosisCard(diagnosis) : ""}
  `);
  const tone = run.status === "FAILED" || run.publishStatus === "FAILED"
    ? "red"
    : diagnosis
      ? "amber"
      : "green";
  const timeMeta = [
    `started ${formatDateTime(run.startedAt)}`,
    run.finishedAt ? `finished ${formatDateTime(run.finishedAt)}` : null,
  ].filter(Boolean).join(" · ");
  return `
    <article class="run-card run-card--${tone}" data-action="open-detail" data-detail-key="${detailKey}">
      <div class="run-card__header">
        <div>
          <div class="run-card__eyebrow">${escapeHtml(run.trigger || "MANUAL")} · #${escapeHtml(String(run.id || "—"))}</div>
          <h3 class="run-card__title">${escapeHtml(runDisplayTitle(run))}</h3>
          <div class="run-card__meta">${escapeHtml(timeMeta)}</div>
        </div>
        <div class="status-stack">
          ${renderStatusChip(run.status, run.status === "FAILED" ? "red" : run.status === "COMPLETED" ? "green" : "blue")}
          ${run.publishStatus ? renderStatusChip(run.publishStatus, run.publishStatus === "FAILED" ? "red" : inferTone(run.publishStatus)) : ""}
        </div>
      </div>
      <div class="chips" style="margin-top:12px;">
        <span class="chip">models=${escapeHtml(String(run.modelsSynced || 0))}</span>
        <span class="chip">values=${escapeHtml(String(run.canonicalValuesSynced || 0))}</span>
        <span class="chip">aliases=${escapeHtml(String(run.aliasesSynced || 0))}</span>
        <span class="chip">review=${escapeHtml(String(run.reviewQueueSize || 0))}</span>
      </div>
      <div class="run-card__summary">${escapeHtml(summarizeRun(run))}</div>
      ${diagnosis ? `<div class="run-card__hint">${escapeHtml(diagnosis.actions?.[0] || diagnosis.summary)}</div>` : ""}
    </article>
  `;
}

function renderReviewCard(item) {
  const detailKey = rememberDetail(`review:${item.clusterKey}`, `${item.attributeCode}: ${item.normalizedValue}`, item, `
    <div class="chips">
      ${renderStatusChip(item.recommendation)}
      ${renderStatusChip(item.recommendedDisposition)}
      <span class="chip">score=${formatNumber(item.totalScore)}</span>
      <span class="chip">candidates=${item.candidateCount}</span>
    </div>
  `);
  const primaryCandidateId = item.primaryCandidateId || item.candidateIds?.[0];
  return `
    <article class="review-card" data-detail-key="${detailKey}">
      <div class="review-card__header">
        <div>
          <div class="review-card__eyebrow">${escapeHtml(item.attributeCode)}</div>
          <h3 class="review-card__title">${escapeHtml(item.normalizedValue)}</h3>
        </div>
        ${renderStatusChip(item.recommendedDisposition)}
      </div>
      <div class="review-card__subtext">${escapeHtml(item.recommendation || "No recommendation")}</div>
      <div class="reason-list" style="margin-top:12px;">
        ${(item.reasons || []).map((reason) => `<span class="reason">${escapeHtml(reason)}</span>`).join("") || `<span class="reason">Reasons missing</span>`}
      </div>
      <div class="review-card__actions" style="margin-top:14px;">
        <button class="button button--success" data-action="review-action" data-review-action="APPROVE" data-candidate-id="${escapeHtml(String(primaryCandidateId || ""))}" ${primaryCandidateId ? "" : "disabled"}>Approve</button>
        <button class="button button--primary" data-action="review-action" data-review-action="PROMOTE" data-candidate-id="${escapeHtml(String(primaryCandidateId || ""))}" ${primaryCandidateId ? "" : "disabled"}>Promote</button>
        <button class="button button--danger" data-action="review-action" data-review-action="REJECT" data-candidate-id="${escapeHtml(String(primaryCandidateId || ""))}" ${primaryCandidateId ? "" : "disabled"}>Reject</button>
        <button class="button button--ghost" data-action="open-detail" data-detail-key="${detailKey}">Inspect</button>
      </div>
    </article>
  `;
}

function renderPublishRow(event) {
  const diagnosis = diagnoseOperationalError(event.details);
  const detailKey = rememberDetail(`publish:${event.id}`, `${event.eventType} / ${event.artifactType}`, event, `
    <div class="chips">
      ${renderStatusChip(event.status)}
      <span class="chip">${escapeHtml(event.artifactType)}</span>
      <span class="chip">${escapeHtml(event.eventType)}</span>
    </div>
    ${diagnosis ? renderOperationalDiagnosisCard(diagnosis) : ""}
  `);
  return `
    <tr data-action="open-detail" data-detail-key="${detailKey}">
      <td>${escapeHtml(formatDateTime(event.createdAt))}</td>
      <td>${escapeHtml(event.eventType)}</td>
      <td>${escapeHtml(event.artifactType)}</td>
      <td>${renderStatusChip(event.status)}</td>
      <td>${escapeHtml(event.entityRef || "—")}</td>
      <td>${escapeHtml(summarizePublish(event))}</td>
    </tr>
  `;
}

function getFilteredModelEnrichmentQueue() {
  if (state.filters.enrichmentStatus === "ALL") return state.modelEnrichmentQueue;
  return state.modelEnrichmentQueue.filter((item) => item.status === state.filters.enrichmentStatus);
}

function countReadyEnrichmentCandidates() {
  return state.modelEnrichmentQueue.filter((item) => item.status === "READY_FOR_OFFICIAL_ENRICHMENT").length;
}

function getEnrichmentDraft(candidate) {
  const candidateId = String(candidate.id || "");
  const existing = state.enrichmentDrafts[candidateId] || {};
  return {
    sourceUri: existing.sourceUri || candidate.suggestedSourceUri || "",
    parserType: existing.parserType || candidate.suggestedParserType || "GENERIC_PHONE_SPECS_PAGE",
    modelLine: existing.modelLine || candidate.familyRaw || "",
    releaseDate: existing.releaseDate || "",
    releaseYear: existing.releaseYear || "",
  };
}

function renderEnrichmentCandidateCard(candidate) {
  const detailKey = rememberDetail(
    `model-enrichment:${candidate.id}`,
    `${candidate.brandRaw} ${candidate.modelRaw}`,
    candidate,
    `
      <div class="chips">
        ${renderStatusChip(candidate.status, enrichmentStatusTone(candidate.status))}
        <span class="chip">observed=${escapeHtml(String(candidate.observedCount))}</span>
        <span class="chip">sellers=${escapeHtml(String(candidate.distinctSellerCount))}</span>
        <span class="chip">confidence=${escapeHtml(formatPercent(candidate.maxConfidence))}</span>
        ${candidate.officialSourceCode ? `<span class="chip">source=${escapeHtml(candidate.officialSourceCode)}</span>` : ""}
      </div>
    `,
  );
  const draft = getEnrichmentDraft(candidate);
  const sourceRegistry = candidate.officialSourceCode
    ? state.sources.find((source) => source.sourceCode === candidate.officialSourceCode)
    : null;
  const readyForPromotion = candidate.status === "READY_FOR_OFFICIAL_ENRICHMENT" && candidate.officialSourceCode;

  return `
    <article class="review-card" data-detail-key="${detailKey}">
      <div class="review-card__header">
        <div>
          <div class="review-card__eyebrow">${escapeHtml(candidate.brandRaw)}${candidate.familyRaw ? ` · ${escapeHtml(candidate.familyRaw)}` : ""}</div>
          <h3 class="review-card__title">${escapeHtml(candidate.modelRaw)}</h3>
        </div>
        ${renderStatusChip(candidate.status, enrichmentStatusTone(candidate.status))}
      </div>
      <div class="review-card__subtext">
        ${escapeHtml(candidate.metadata?.sampleTitle || "Runtime candidate без sampleTitle.")}
      </div>
      <div class="chips" style="margin-top:12px;">
        <span class="chip">observed=${escapeHtml(String(candidate.observedCount))}</span>
        <span class="chip">sellers=${escapeHtml(String(candidate.distinctSellerCount))}</span>
        <span class="chip">confidence=${escapeHtml(formatPercent(candidate.maxConfidence))}</span>
        ${candidate.brandCode ? `<span class="chip">brand=${escapeHtml(candidate.brandCode)}</span>` : ""}
        ${candidate.canonicalModelCode ? `<span class="chip">canonical=${escapeHtml(candidate.canonicalModelCode)}</span>` : ""}
        ${candidate.officialSourceCode ? `<span class="chip">official=${escapeHtml(candidate.officialSourceCode)}</span>` : `<span class="chip">official=—</span>`}
        ${candidate.suggestedSourceUri ? `<span class="chip">auto-url=${escapeHtml(candidate.sourceUriSuggestionConfidence || "SUGGESTED")}</span>` : ""}
        ${candidate.officialEndpointCode ? `<span class="chip">endpoint=${escapeHtml(candidate.officialEndpointCode)}</span>` : ""}
      </div>
      <div class="reason-list" style="margin-top:12px;">
        ${(candidate.reasonCodes || []).map((reason) => `<span class="reason">${escapeHtml(reason)}</span>`).join("") || `<span class="reason">reasonCodes empty</span>`}
      </div>
      <div class="meta-list" style="margin-top:14px;">
        <span class="meta">Updated: ${escapeHtml(formatDateTime(candidate.updatedAt))}</span>
        <span class="meta">Offers: ${escapeHtml((candidate.sampleOfferRefs || []).join(", ") || "—")}</span>
        <span class="meta">Sellers: ${escapeHtml((candidate.sellerRefs || []).join(", ") || "—")}</span>
      </div>
      ${readyForPromotion ? `
        <div class="field" style="margin-top:16px;">
          <span class="field__label">Official source URL</span>
          <input class="field__input" data-input="enrichment-source-uri" data-candidate-id="${escapeHtml(String(candidate.id))}" value="${escapeHtml(draft.sourceUri)}" placeholder="https://brand.example.com/phones/model-page/">
          <div class="field__hint">${candidate.suggestedSourceUri ? `Автоподсказка: ${escapeHtml(candidate.suggestedSourceUri)}` : "Если поле пустое, promotion попробует взять auto-suggestion для supported brand."}</div>
        </div>
        <div class="field__inline">
          <label class="field">
            <span class="field__label">Parser</span>
            <select class="field__input" data-change="enrichment-parser-type" data-candidate-id="${escapeHtml(String(candidate.id))}">
              ${[
                "GENERIC_PHONE_SPECS_PAGE",
                "APPLE_BUY_IPHONE_METRICS",
                "SAMSUNG_DEVICE_BUY_PAGE",
                "GOOGLE_PIXEL_SUPPORT_SPECS",
                "XIAOMI_GLOBAL_SPECS_PAGE",
                "ONEPLUS_SPECS_PAGE",
                "NOTHING_PRODUCT_PAGE",
              ].map((parserType) => `<option value="${parserType}"${draft.parserType === parserType ? " selected" : ""}>${parserType}</option>`).join("")}
            </select>
          </label>
          <label class="field">
            <span class="field__label">Model line</span>
            <input class="field__input" data-input="enrichment-model-line" data-candidate-id="${escapeHtml(String(candidate.id))}" value="${escapeHtml(draft.modelLine)}" placeholder="Honor 400">
          </label>
        </div>
        <div class="field__inline">
          <label class="field">
            <span class="field__label">Release date</span>
            <input class="field__input" data-input="enrichment-release-date" data-candidate-id="${escapeHtml(String(candidate.id))}" value="${escapeHtml(draft.releaseDate)}" placeholder="2026-04-01">
          </label>
          <label class="field">
            <span class="field__label">Release year</span>
            <input class="field__input" data-input="enrichment-release-year" data-candidate-id="${escapeHtml(String(candidate.id))}" value="${escapeHtml(String(draft.releaseYear || ""))}" placeholder="2026">
          </label>
        </div>
        <div class="table-card__subtext">`release_date` необязателен. Если его нет, promotion всё равно создаст overlay и сможет сразу прогнать refresh.</div>
      ` : ""}
      <div class="review-card__actions" style="margin-top:14px;">
        ${readyForPromotion ? `<button class="button button--primary" data-action="promote-enrichment-candidate" data-candidate-id="${escapeHtml(String(candidate.id))}">Promote to official</button>` : ""}
        ${sourceRegistry ? `<button class="button button--ghost" data-action="run-source" data-registry-code="${escapeHtml(sourceRegistry.registryCode)}">Прогнать source</button>` : ""}
        <button class="button button--ghost" data-action="open-detail" data-detail-key="${detailKey}">Inspect</button>
      </div>
    </article>
  `;
}

function renderDrawer() {
  if (!state.selectedDetailKey || !state.detailRegistry.has(state.selectedDetailKey)) {
    refs.shell?.classList.add("shell--drawer-empty");
    refs.drawer.classList.add("drawer--empty");
    refs.drawerTitle.textContent = "Ничего не выбрано";
    refs.drawerBody.innerHTML = `<p class="drawer__placeholder">Кликните на ветку, source, run или событие, чтобы увидеть структурированные детали без копания в логах.</p>`;
    return;
  }
  refs.shell?.classList.remove("shell--drawer-empty");
  const detail = state.detailRegistry.get(state.selectedDetailKey);
  refs.drawer.classList.remove("drawer--empty");
  refs.drawerTitle.textContent = detail.title;
  refs.drawerBody.innerHTML = `
    ${detail.summaryHtml || ""}
    <div class="detail-card">
      <div class="table-card__eyebrow">Structured payload</div>
      <h3 class="panel__title">JSON details</h3>
      <pre class="json-box">${escapeHtml(JSON.stringify(detail.payload, null, 2))}</pre>
    </div>
  `;
}

function rememberDetail(key, title, payload, summaryHtml = "") {
  state.detailRegistry.set(key, { title, payload, summaryHtml });
  return key;
}

async function handleClick(event) {
  const actionEl = event.target.closest("[data-action]");
  if (!actionEl) {
    if (state.openHelpKey && !event.target.closest(".context-help-anchor")) {
      state.openHelpKey = null;
      renderSectionHeader();
    }
    return;
  }
  const action = actionEl.dataset.action;

  try {
    if (action !== "toggle-help" && state.openHelpKey) {
      state.openHelpKey = null;
      renderSectionHeader();
    }
    if (action === "toggle-help") {
      const helpKey = actionEl.dataset.helpKey || state.activeSection;
      state.openHelpKey = state.openHelpKey === helpKey ? null : helpKey;
      renderSectionHeader();
      return;
    }
    if (action === "switch-section") {
      state.activeSection = actionEl.dataset.section;
      state.openHelpKey = null;
      localStorage.setItem("admin.activeSection", state.activeSection);
      render();
      return;
    }
    if (action === "reload-all") {
      await reloadAll();
      return;
    }
    if (action === "save-backend-url") {
      await saveBackendUrl();
      return;
    }
    if (action === "trigger-refresh-all") {
      await triggerRefresh();
      return;
    }
    if (action === "trigger-rebuild") {
      await triggerRebuild();
      return;
    }
    if (action === "select-category") {
      const categoryCode = actionEl.dataset.categoryCode;
      if (categoryCode) {
        state.selectedCategory = categoryCode;
        localStorage.setItem("admin.selectedCategory", categoryCode);
        renderCategorySelect();
        await loadBranchData();
        render();
      }
      if (actionEl.dataset.detailKey) {
        state.selectedDetailKey = actionEl.dataset.detailKey;
        renderDrawer();
      }
      return;
    }
    if (action === "run-source") {
      await triggerRefresh([actionEl.dataset.registryCode]);
      return;
    }
    if (action === "open-detail") {
      state.selectedDetailKey = actionEl.dataset.detailKey;
      renderDrawer();
      return;
    }
    if (action === "clear-detail") {
      state.selectedDetailKey = null;
      renderDrawer();
      return;
    }
    if (action === "review-action") {
      await submitReviewAction(
        actionEl.dataset.candidateId,
        actionEl.dataset.reviewAction,
      );
      return;
    }
    if (action === "promote-enrichment-candidate") {
      await promoteModelEnrichmentCandidate(actionEl.dataset.candidateId);
      return;
    }
    if (action === "load-runtime-lab") {
      await loadRuntimeLabSnapshot();
      return;
    }
    if (action === "runtime-use-default-attrs") {
      state.runtimeLab.attributeCodesText = DEFAULT_RUNTIME_ATTRIBUTE_CODES.join(", ");
      render();
      return;
    }
    if (action === "focus-attribute") {
      focusRuntimeAttribute(actionEl.dataset.attributeCode || "");
      return;
    }
    if (action === "use-runtime-brand") {
      state.runtimeLab.brand = actionEl.dataset.runtimeBrand || "";
      state.runtimeLab.model = "";
      render();
      return;
    }
    if (action === "use-runtime-model") {
      state.runtimeLab.model = actionEl.dataset.runtimeModel || "";
      render();
      return;
    }
    if (action === "run-query-flow") {
      await runQueryFlow();
      return;
    }
    if (action === "query-use-default-attrs") {
      state.queryFlow.attributeCodesText = DEFAULT_RUNTIME_ATTRIBUTE_CODES.join(", ");
      render();
      return;
    }
    if (action === "use-query-sample") {
      state.queryFlow.rawQuery = actionEl.dataset.query || "";
      render();
      return;
    }
    if (action === "apply-query-result-to-runtime") {
      if (state.queryFlow.result) {
        state.runtimeLab.brand = state.queryFlow.result.inferredBrand || "";
        state.runtimeLab.model = state.queryFlow.result.inferredModel || "";
        state.runtimeLab.attributeCodesText = state.queryFlow.attributeCodesText;
        state.runtimeLab.snapshot = state.queryFlow.result.detailedSnapshot || state.branchRuntimeSnapshot;
        state.activeSection = "runtime-lab";
        localStorage.setItem("admin.activeSection", state.activeSection);
        render();
      }
      return;
    }
  } catch (error) {
    showToast(error.message || "Операция завершилась ошибкой", "danger");
  }
}

function handleInput(event) {
  const inputEl = event.target;
  const inputName = inputEl.dataset.input;
  if (!inputName) return;
  if (inputName === "branch-search") {
    state.filters.branchSearch = inputEl.value;
    render();
    return;
  }
  if (inputName === "source-search") {
    state.filters.sourceSearch = inputEl.value;
    render();
    return;
  }
  if (inputName === "enrichment-source-uri" || inputName === "enrichment-model-line" || inputName === "enrichment-release-date" || inputName === "enrichment-release-year") {
    const candidateId = String(inputEl.dataset.candidateId || "");
    if (!candidateId) return;
    const existing = state.enrichmentDrafts[candidateId] || {};
    state.enrichmentDrafts[candidateId] = {
      ...existing,
      sourceUri: inputName === "enrichment-source-uri" ? inputEl.value : existing.sourceUri || "",
      modelLine: inputName === "enrichment-model-line" ? inputEl.value : existing.modelLine || "",
      releaseDate: inputName === "enrichment-release-date" ? inputEl.value : existing.releaseDate || "",
      releaseYear: inputName === "enrichment-release-year" ? inputEl.value : existing.releaseYear || "",
      parserType: existing.parserType || "GENERIC_PHONE_SPECS_PAGE",
    };
    return;
  }
  if (inputName === "runtime-brand") state.runtimeLab.brand = inputEl.value;
  if (inputName === "runtime-model") state.runtimeLab.model = inputEl.value;
  if (inputName === "runtime-locale") state.runtimeLab.locale = inputEl.value;
  if (inputName === "runtime-attributes") state.runtimeLab.attributeCodesText = inputEl.value;
  if (inputName === "query-flow-raw") state.queryFlow.rawQuery = inputEl.value;
  if (inputName === "query-flow-locale") state.queryFlow.locale = inputEl.value;
  if (inputName === "query-flow-attributes") state.queryFlow.attributeCodesText = inputEl.value;
}

async function handleChange(event) {
  const selectEl = event.target;
  if (selectEl === refs.categorySelect) {
    state.selectedCategory = selectEl.value;
    localStorage.setItem("admin.selectedCategory", state.selectedCategory);
    await loadBranchData();
    render();
    return;
  }
  const changeName = selectEl.dataset.change;
  if (!changeName) return;
  if (changeName === "enrichment-parser-type") {
    const candidateId = String(selectEl.dataset.candidateId || "");
    if (!candidateId) return;
    const existing = state.enrichmentDrafts[candidateId] || {};
    state.enrichmentDrafts[candidateId] = {
      ...existing,
      parserType: selectEl.value,
    };
    return;
  }
  if (changeName === "enrichment-status-filter") state.filters.enrichmentStatus = selectEl.value;
  if (changeName === "run-status-filter") state.filters.runStatus = selectEl.value;
  if (changeName === "publish-status-filter") state.filters.publishStatus = selectEl.value;
  if (changeName === "contract-status-filter") state.filters.contractStatus = selectEl.value;
  render();
}

async function saveBackendUrl() {
  const backendUrl = refs.backendUrlInput.value.trim();
  if (!backendUrl) {
    showToast("Укажи backend URL.", "danger");
    return;
  }
  const response = await fetch("/__admin/config", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ backendUrl }),
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(`Не удалось сохранить backend URL: ${text}`);
  }
  state.backendConfig = await response.json();
  refs.backendUrlInput.value = state.backendConfig.backendUrl;
  await reloadAll();
  showToast(
    isBackendHealthy()
      ? `Backend URL обновлён: ${state.backendConfig.backendUrl}`
      : `Backend URL сохранён, но control plane всё ещё не отвечает: ${state.backendConfig.backendUrl}`,
    isBackendHealthy() ? "success" : "danger",
  );
}

async function reloadAll() {
  let inventoryLoaded = false;
  try {
    await loadReadinessInventory();
    inventoryLoaded = true;
  } catch (error) {
    resetBranchDataAfterFailure();
    showToast(error.message || "Не удалось обновить inventory веток", "danger");
  }
  if (inventoryLoaded) {
    await loadBranchData();
  }
  render();
  if (isBackendHealthy()) {
    showToast(`Данные обновлены для ${state.selectedCategory}`, "success");
  }
}

async function triggerRefresh(registryCodes = []) {
  const params = { categoryCode: state.selectedCategory, trigger: "WEB_ADMIN_MANUAL" };
  if (registryCodes.length > 0) params.registryCode = registryCodes;
  const result = await apiPost(API_PATHS.refresh, params);
  await loadBranchData();
  render();
  showToast(`Refresh запущен: ${result.runs?.length || 0} run(s).`, "success");
}

async function triggerRebuild() {
  const event = await apiPost(API_PATHS.rebuild, {
    categoryCode: state.selectedCategory,
    reason: "WEB_ADMIN_MANUAL_REBUILD",
  });
  await loadBranchData();
  render();
  showToast(`Rebuild отправлен: ${event.status || "UNKNOWN"}`, "success");
}

function focusRuntimeAttribute(attributeCode) {
  const codes = new Set(parseAttributeCodes(state.runtimeLab.attributeCodesText));
  codes.add("model");
  if (attributeCode) {
    codes.add(attributeCode);
  }
  state.runtimeLab.attributeCodesText = Array.from(codes).join(", ");
  state.activeSection = "runtime-lab";
  localStorage.setItem("admin.activeSection", state.activeSection);
  render();
}

async function submitReviewAction(candidateIdRaw, action) {
  const candidateId = Number(candidateIdRaw);
  if (!candidateId || !action) {
    showToast("Для действия review нужен candidateId.", "danger");
    return;
  }
  const reasonCode = window.prompt("Reason code (optional)", "WEB_ADMIN_REVIEW") || "";
  const result = await apiPost(API_PATHS.reviewAction, {
    categoryCode: state.selectedCategory,
    candidateId,
    action,
    actor: "admin-web",
    reasonCode,
  });
  await loadBranchData();
  render();
  showToast(`${action} выполнено для candidate ${result.candidateId}.`, "success");
}

async function promoteModelEnrichmentCandidate(candidateIdRaw) {
  const candidateId = Number(candidateIdRaw);
  if (!candidateId) {
    showToast("Для promotion нужен candidateId.", "danger");
    return;
  }
  const candidate = state.modelEnrichmentQueue.find((item) => Number(item.id) === candidateId);
  if (!candidate) {
    showToast("Кандидат не найден в текущей queue.", "danger");
    return;
  }
  const draft = getEnrichmentDraft(candidate);
  const response = await apiPostJson(`${API_PATHS.modelEnrichmentPromote}?categoryCode=${encodeURIComponent(state.selectedCategory)}`, {
    candidateId,
    actor: "admin-web",
    sourceUri: draft.sourceUri?.trim() || null,
    parserType: (draft.parserType || "GENERIC_PHONE_SPECS_PAGE").trim(),
    modelLine: draft.modelLine?.trim() || null,
    releaseDate: draft.releaseDate?.trim() || null,
    releaseYear: draft.releaseYear ? Number(draft.releaseYear) : null,
    triggerRefresh: true,
  });
  await loadBranchData();
  render();
  showToast(
    `Promotion создан: ${response.endpointCode}${response.runs?.length ? ` · refresh ${response.runs.length}` : ""}.`,
    "success",
  );
}

async function loadRuntimeLabSnapshot() {
  const attributeCodes = state.runtimeLab.attributeCodesText
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const snapshot = await apiGet(API_PATHS.liveValues, {
    categoryCode: state.selectedCategory,
    brand: state.runtimeLab.brand || undefined,
    model: state.runtimeLab.model || undefined,
    locale: state.runtimeLab.locale || undefined,
    attributeCode: attributeCodes,
  });
  state.runtimeLab.snapshot = snapshot;
  render();
  showToast("Runtime snapshot загружен.", "success");
}

function isBackendHealthy() {
  return state.connection.status === "healthy";
}

function connectionSummary() {
  const backendUrl = state.backendConfig?.backendUrl || "backend не настроен";
  const label = (
    state.connection.status === "healthy" ? "CONNECTED"
      : state.connection.status === "api-mismatch" ? "API MISMATCH"
        : state.connection.status === "unreachable" ? "OFFLINE"
          : "UNKNOWN"
  );
  const tone = (
    state.connection.status === "healthy" ? "green"
      : state.connection.status === "unknown" ? "amber"
        : "red"
  );
  const text = state.connection.message || `Проверь ${backendUrl}`;
  return { label, tone, text };
}

function deriveConnectionAlert() {
  if (state.connection.status === "healthy" || state.connection.status === "unknown") {
    return null;
  }
  const backendUrl = state.backendConfig?.backendUrl || "—";
  const details = state.connection.httpStatus
    ? `${state.connection.message} · HTTP ${state.connection.httpStatus}`
    : state.connection.message;
  return {
    tone: "danger",
    title: state.connection.status === "api-mismatch"
      ? "Admin подключён не к тому backend API"
      : "Backend control plane недоступен",
    text: `${details}. Текущий backend: ${backendUrl}`,
  };
}

function markBackendHealthy(context = "") {
  state.connection = {
    status: "healthy",
    message: context ? `Control plane отвечает: ${context}` : "Control plane отвечает.",
    context,
    httpStatus: 200,
    lastSuccessAt: Date.now(),
    lastFailureAt: state.connection.lastFailureAt,
  };
}

function markBackendFailure(error, context = "") {
  const message = String(error?.message || "Неизвестная ошибка backend");
  const httpStatus = Number(error?.httpStatus) || null;
  const status = httpStatus === 404
    ? "api-mismatch"
    : (httpStatus === 502 || /unreachable|refused|timed out|timeout|failed to fetch/i.test(message)
      ? "unreachable"
      : "api-mismatch");
  state.connection = {
    status,
    message,
    context,
    httpStatus,
    lastSuccessAt: state.connection.lastSuccessAt,
    lastFailureAt: Date.now(),
  };
}

function resetBranchDataAfterFailure() {
  state.refreshStatus = null;
  state.sources = [];
  state.modelEnrichmentQueue = [];
  state.runs = [];
  state.reviewQueue = [];
  state.publishEvents = [];
  state.branchSpec = null;
  state.branchRuntimeSnapshot = null;
  state.runtimeLab.snapshot = null;
  state.queryFlow.result = null;
}

async function apiGet(path, params = {}) {
  return doApiRequest({ method: "GET", path, params, trackConnection: true });
}

async function safeApiGet(path, params = {}, fallback = null) {
  try {
    return await doApiRequest({ method: "GET", path, params, trackConnection: false });
  } catch {
    return fallback;
  }
}

async function apiPost(path, params = {}) {
  return doApiRequest({ method: "POST", path, params, trackConnection: true });
}

async function apiPostJson(path, body) {
  return doApiRequest({ method: "POST", path, body, isJson: true, trackConnection: true });
}

async function doApiRequest({ method, path, params = {}, body = null, isJson = false, trackConnection = true }) {
  const url = new URL(`/__admin/api${path}`, window.location.origin);
  appendSearchParams(url, params);
  const init = { method };
  if (isJson) {
    init.headers = { "Content-Type": "application/json" };
    init.body = JSON.stringify(body);
  }
  try {
    const payload = await fetchJson(url.toString(), init);
    if (trackConnection) {
      markBackendHealthy(path);
    }
    return payload;
  } catch (error) {
    if (trackConnection) {
      markBackendFailure(error, path);
    }
    throw error;
  }
}

function appendSearchParams(url, params) {
  Object.entries(params).forEach(([key, value]) => {
    if (value == null || value === "") return;
    if (Array.isArray(value)) {
      value.forEach((item) => {
        if (item != null && item !== "") url.searchParams.append(key, String(item));
      });
      return;
    }
    url.searchParams.set(key, String(value));
  });
}

async function fetchJson(url, init = undefined) {
  const response = await fetch(url, init);
  return parseJsonResponse(response);
}

async function parseJsonResponse(response) {
  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = text;
    }
  }
  if (!response.ok) {
    const message = typeof payload === "string"
      ? summarizeBackendTextError(payload, response.status)
      : payload?.details || payload?.error || payload?.message || `HTTP ${response.status}`;
    const error = new Error(message);
    error.httpStatus = response.status;
    error.payload = payload;
    throw error;
  }
  return payload;
}

function summarizeBackendTextError(text, status) {
  const raw = String(text || "").trim();
  if (!raw) return `HTTP ${status}`;
  if (/<html|<!doctype/i.test(raw)) {
    if (status === 404) return "Catalog API route not found on backend";
    return `Backend returned HTML instead of JSON (HTTP ${status})`;
  }
  return raw.length > 220 ? `${raw.slice(0, 220)}…` : raw;
}

function getSelectedReadiness() {
  return state.readiness.find((item) => item.category?.code === state.selectedCategory) || null;
}

function countReadyBranches() {
  return state.readiness.filter((item) => item.readiness === "READY").length;
}

function countCriticalBranches() {
  return state.readiness.filter((item) => item.readiness === "CRITICAL").length;
}

function runOccurredAt(run) {
  return Number(run?.finishedAt || run?.startedAt || 0);
}

function publishOccurredAt(event) {
  return Number(event?.createdAt || 0);
}

function isRunFailure(run) {
  return Boolean(run) && (run.status === "FAILED" || run.publishStatus === "FAILED");
}

function isRunSuccess(run) {
  return Boolean(run) && run.status === "COMPLETED" && run.publishStatus !== "FAILED";
}

function isStandalonePublishFailure(event) {
  return Boolean(event) && event.status === "FAILED" && !event.refreshRunId;
}

function isPublishSuccess(event) {
  return Boolean(event) && event.status === "PUBLISHED";
}

function buildOperationalEntry(kind, payload) {
  if (!payload) return null;
  return {
    kind,
    payload,
    timestamp: kind === "publish" ? publishOccurredAt(payload) : runOccurredAt(payload),
  };
}

function laterOperationalEntry(left, right) {
  if (!left) return right;
  if (!right) return left;
  return left.timestamp >= right.timestamp ? left : right;
}

function operationalEntrySummary(entry) {
  if (!entry) return "—";
  return entry.kind === "publish"
    ? summarizePublish(entry.payload)
    : summarizeRun(entry.payload);
}

function operationalEntryDiagnosis(entry) {
  if (!entry) return null;
  return entry.kind === "publish"
    ? diagnoseOperationalError(entry.payload.details)
    : diagnoseOperationalError(entry.payload.errorMessage);
}

function operationalEntryLabel(entry) {
  if (!entry) return "—";
  if (entry.kind === "publish") {
    return entry.payload.entityRef || entry.payload.eventType || "publish";
  }
  return entry.payload.registryCode || "run";
}

function collectOperationalSnapshot(runs = state.runs, publishEvents = state.publishEvents) {
  const safeRuns = Array.isArray(runs) ? runs : [];
  const safePublishEvents = Array.isArray(publishEvents) ? publishEvents : [];
  const latestRun = safeRuns[0] || null;
  const latestPublish = safePublishEvents[0] || null;
  const latestFailure = laterOperationalEntry(
    buildOperationalEntry("run", safeRuns.find(isRunFailure)),
    buildOperationalEntry("publish", safePublishEvents.find(isStandalonePublishFailure)),
  );
  const latestSuccess = laterOperationalEntry(
    buildOperationalEntry("run", safeRuns.find(isRunSuccess)),
    buildOperationalEntry("publish", safePublishEvents.find(isPublishSuccess)),
  );
  const successCutoff = latestSuccess?.timestamp || 0;
  const activeFailedRuns = safeRuns.filter((run) => isRunFailure(run) && runOccurredAt(run) > successCutoff);
  const historicalFailedRuns = safeRuns.filter((run) => isRunFailure(run) && runOccurredAt(run) <= successCutoff);
  const standalonePublishFailures = safePublishEvents.filter(isStandalonePublishFailure);
  const activeStandalonePublishFailures = standalonePublishFailures.filter((event) => publishOccurredAt(event) > successCutoff);
  const historicalStandalonePublishFailures = standalonePublishFailures.filter((event) => publishOccurredAt(event) <= successCutoff);
  const activeFailure = latestFailure && latestFailure.timestamp > successCutoff ? latestFailure : null;
  return {
    latestRun,
    latestPublish,
    latestFailure,
    latestSuccess,
    activeFailure,
    activeFailedRuns,
    historicalFailedRuns,
    activeStandalonePublishFailures,
    historicalStandalonePublishFailures,
  };
}

function inferRuntimeBrand() {
  const firstSourceBrand = state.sources.find((source) => source.metadata?.brand)?.metadata?.brand;
  return firstSourceBrand || "";
}

function deriveNextAction(selectedReadiness, operational) {
  const diagnosis = operationalEntryDiagnosis(operational?.activeFailure);
  if (diagnosis) {
    return {
      title: diagnosis.title,
      description: diagnosis.actions?.[0] || diagnosis.summary,
      tone: "red",
    };
  }
  if (operational?.activeFailure) {
    return {
      title: "Open active failure",
      description: `${operationalEntryLabel(operational.activeFailure)} · ${operationalEntrySummary(operational.activeFailure)}`,
      tone: "red",
    };
  }
  if (state.reviewQueue.length > 0) {
    return {
      title: "Review candidates",
      description: "В очереди есть кандидаты. Разбери их до следующего publish, чтобы runtime не расходился с governance.",
      tone: "amber",
    };
  }
  if (selectedReadiness && selectedReadiness.readiness !== "READY") {
    return {
      title: "Close blockers",
      description: "Ветка ещё не READY. Открой coverage и добей blocking issues, а потом повтори refresh.",
      tone: "amber",
    };
  }
  return {
    title: "Run targeted refresh",
    description: "Ветка выглядит здоровой. Следующий шаг — source-level refresh или runtime lab для smoke-проверки UI.",
    tone: "green",
  };
}

function readinessSummary(item) {
  if (!item) return "Нет readiness inventory для ветки.";
  if (item.readiness === "READY") return "Ветка готова по текущему inventory и completeness gate.";
  const blockers = (item.blockingIssues || []).slice(0, 3).join(" • ");
  return blockers || "Есть редакционные или operational blockers.";
}

function summarizeRun(run) {
  if (!run) return "—";
  const diagnosis = diagnoseOperationalError(run.errorMessage);
  if (diagnosis) return diagnosis.shortText;
  if (run.errorMessage) return run.errorMessage.split("\n")[0].trim();
  return `models=${run.modelsSynced || 0}, values=${run.canonicalValuesSynced || 0}, aliases=${run.aliasesSynced || 0}, publish=${run.publishStatus || "—"}`;
}

function summarizePublish(event) {
  if (!event) return "—";
  const diagnosis = diagnoseOperationalError(event.details);
  if (diagnosis) return diagnosis.shortText;
  return event.details?.split("\n")[0]?.trim() || `${event.eventType} / ${event.status}`;
}

function diagnoseOperationalError(rawText) {
  const text = String(rawText || "");
  if (!text) return null;
  const attributeHintCollisions = [...text.matchAll(/alias_collision:[^|,\n]+\|ATTRIBUTE_HINT\|([^,\n]+?)->([A-Za-z0-9_\/]+)/gi)];
  if (attributeHintCollisions.length > 0) {
    const [aliasValue, targetCodes] = attributeHintCollisions[0].slice(1);
    const targets = String(targetCodes || "")
      .split("/")
      .map((item) => item.trim())
      .filter(Boolean);
    const targetSummary = targets.join(", ") || "несколько attribute code";
    return {
      kind: "attribute-hint-collision",
      title: "Published attribute hint конфликтует",
      summary: `Слишком общий hint "${aliasValue}" одновременно указывает на ${targetSummary}, поэтому rebuild/publish останавливается на serving validation.`,
      shortText: `Alias collision: ${aliasValue} -> ${targetSummary}.`,
      actions: [
        `Убери bare-number hint "${aliasValue}" или оставь только контекстные варианты вроде "8gb ram" / "128 gb".`,
        "После правки alias-ов повтори Rebuild branch или targeted refresh нужного source.",
      ],
    };
  }
  const staleContract = /catalog_governance_value_canon_attribute_code_fkey|attribute_defs/i.test(text);
  if (staleContract) {
    const matchedAttributeCodes = [...text.matchAll(/\(attribute_code\)=\(([^)]+)\)/gi)]
      .map((match) => match[1])
      .filter(Boolean);
    const attributeCodes = [...new Set(matchedAttributeCodes)];
    const attributeSummary = attributeCodes.length > 0 ? attributeCodes.join(", ") : "richer phone attrs";
    return {
      kind: "stale-runtime-contract",
      title: "Runtime contract отстал от product master",
      summary: `Backend пытается публиковать richer facts (${attributeSummary}), но этих attribute code ещё нет в runtime contract / attribute_defs.`,
      shortText: `Runtime contract stale: ${attributeSummary} отсутствует в attribute_defs.`,
      actions: [
        "Перезапусти backend на последнем коде, чтобы self-heal runtime contract реально выполнился.",
        "Нажми Rebuild branch для TECH.PHONES.",
        "После rebuild повтори refresh нужных source-ов.",
      ],
    };
  }
  if (/socket timeout|timed out|timeout expired|failed to fetch|connection reset/i.test(text)) {
    return {
      kind: "connector-timeout",
      title: "Source refresh упёрся в сетевой таймаут",
      summary: "Источник или backend не завершил refresh за ожидаемое время. Это не schema gap, а transport/connectivity issue.",
      shortText: "Source timeout during refresh/publish.",
      actions: [
        "Повтори refresh по одному source, а не по всем сразу.",
        "Проверь доступность official source и жив ли backend worker.",
      ],
    };
  }
  return null;
}

function renderOperationalDiagnosisCard(diagnosis) {
  return `
    <div class="detail-card" style="margin-top:14px;">
      <div class="table-card__eyebrow">Operator diagnosis</div>
      <h3 class="panel__title">${escapeHtml(diagnosis.title)}</h3>
      <div class="panel__subtext">${escapeHtml(diagnosis.summary)}</div>
      ${diagnosis.actions?.length ? `
        <ul class="banner__list">
          ${diagnosis.actions.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}
        </ul>
      ` : ""}
    </div>
  `;
}

function sortReadinessRows(left, right) {
  const priority = { CRITICAL: 0, BETA: 1, INTERNAL: 2, READY: 3 };
  const leftPriority = priority[left.readiness] ?? 99;
  const rightPriority = priority[right.readiness] ?? 99;
  if (leftPriority !== rightPriority) return leftPriority - rightPriority;
  return (left.category?.code || "").localeCompare(right.category?.code || "", "en");
}

function toneForReadiness(readiness) {
  if (readiness === "READY") return "green";
  if (readiness === "BETA" || readiness === "INTERNAL") return "amber";
  return "red";
}

function renderStatusChip(label, forcedTone = null) {
  const normalized = String(label || "UNKNOWN").trim();
  const tone = forcedTone || inferTone(normalized);
  return `<span class="status status--${tone}">${escapeHtml(normalized)}</span>`;
}

function inferTone(label) {
  const value = String(label || "").toUpperCase();
  if (["READY", "COMPLETED", "PUBLISHED", "ENABLED", "PASSED", "CLEAN", "SUCCESS", "AUTO PUBLISH", "GREEN"].includes(value)) return "green";
  if (["FAILED", "CRITICAL", "RED"].includes(value)) return "red";
  if (["BETA", "INTERNAL", "REVIEW", "ACTION REQUIRED", "AMBER"].includes(value)) return "amber";
  if (["DISABLED"].includes(value)) return "slate";
  return "blue";
}

function enrichmentStatusTone(status) {
  const normalized = String(status || "").toUpperCase();
  if (normalized === "READY_FOR_OFFICIAL_ENRICHMENT") return "amber";
  if (normalized === "ENRICHED") return "green";
  if (normalized === "REJECTED") return "red";
  return "blue";
}

function renderEmptyState(title, text) {
  return `
    <div class="empty-state">
      <h3 class="empty-state__title">${escapeHtml(title)}</h3>
      <p class="empty-state__text">${escapeHtml(text)}</p>
    </div>
  `;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function formatDateTime(value) {
  if (!value) return "—";
  try {
    return new Intl.DateTimeFormat("ru-RU", {
      dateStyle: "short",
      timeStyle: "short",
    }).format(new Date(value));
  } catch {
    return String(value);
  }
}

function formatDurationShort(ms) {
  if (!ms && ms !== 0) return "—";
  if (ms < 1000) return `${ms} ms`;
  const minutes = Math.round(ms / 60000);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.round((minutes / 60) * 10) / 10;
  return `${hours} h`;
}

function formatNumber(value) {
  if (value == null || Number.isNaN(Number(value))) return "—";
  return new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 2 }).format(Number(value));
}

function formatPercent(value) {
  if (value == null || Number.isNaN(Number(value))) return "—";
  return `${Math.round(Number(value) * 100)}%`;
}

let toastTimeout = null;
function showToast(message, tone = "info") {
  refs.toast.textContent = message;
  refs.toast.className = "toast";
  if (tone === "danger") refs.toast.style.background = "#7f1d1d";
  else if (tone === "success") refs.toast.style.background = "#14532d";
  else refs.toast.style.background = "#0f172a";
  if (toastTimeout) clearTimeout(toastTimeout);
  toastTimeout = setTimeout(() => {
    refs.toast.className = "toast toast--hidden";
  }, 3600);
}
