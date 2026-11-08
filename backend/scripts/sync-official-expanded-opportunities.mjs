import { createHash } from "node:crypto";
import { readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const DATA_DIRECTORY = resolve(SCRIPT_DIRECTORY, "../src/main/resources/data");
const OUTPUT = resolve(DATA_DIRECTORY, "official-expanded-opportunities.json");
const TODAY = process.env.JAMEATI_SNAPSHOT_DATE ?? new Date().toISOString().slice(0, 10);
const PARCOURSUP_LIMIT = Number.parseInt(
  process.env.JAMEATI_PARCOURSUP_LIMIT ?? "5000",
  10,
);
const USER_AGENT =
  "JameatiOfficialCatalogueImporter/1.0 (official metadata sync; https://jamieati.org)";

const SOURCES = {
  europeanYouth:
    "https://youth.europa.eu/go-abroad/volunteering/opportunities_en",
  grantsGov: "https://www.grants.gov/search-grants",
  euFunding:
    "https://ec.europa.eu/info/funding-tenders/opportunities/portal/screen/opportunities/topic-search",
  euFundingApi:
    "https://api.tech.ec.europa.eu/search-api/prod/rest/search",
  mitLearn: "https://learn.mit.edu/",
  mitOcw: "https://ocw.mit.edu/",
  mitEvents: "https://calendar.mit.edu/",
  salto:
    "https://www.salto-youth.net/tools/european-training-calendar/browse/",
  euraxess: "https://euraxess.ec.europa.eu/jobs/search",
  parcoursup:
    "https://data.enseignementsup-recherche.gouv.fr/explore/dataset/fr-esr-cartographie_formations_parcoursup/",
};

const HTML_ENTITIES = new Map([
  ["amp", "&"],
  ["apos", "'"],
  ["gt", ">"],
  ["hellip", "…"],
  ["ldquo", "“"],
  ["lsquo", "‘"],
  ["lt", "<"],
  ["mdash", "—"],
  ["nbsp", " "],
  ["ndash", "–"],
  ["quot", '"'],
  ["rdquo", "”"],
  ["rsquo", "’"],
]);

const ENGLISH_MONTHS = new Map(
  [
    "january",
    "february",
    "march",
    "april",
    "may",
    "june",
    "july",
    "august",
    "september",
    "october",
    "november",
    "december",
  ].map((month, index) => [month, index + 1]),
);

const EYP_TOPICS = new Map([
  ["acce", "الوصول والإتاحة"],
  ["art", "الفنون والثقافة"],
  ["clim", "المناخ والبيئة"],
  ["crea", "الإبداع"],
  ["disa", "دعم ذوي الإعاقة"],
  ["edu", "التعليم والتدريب"],
  ["empl", "التوظيف وريادة الأعمال"],
  ["heal", "الصحة والرفاه"],
  ["incl", "الاندماج الاجتماعي"],
  ["migr", "الهجرة واللاجئون"],
  ["part", "المشاركة المدنية"],
  ["sprt", "الرياضة"],
  ["yout", "العمل الشبابي"],
]);

const DEGREE_LEVELS = new Map([
  ["high school", "ثانوية عامة"],
  ["undergraduate", "بكالوريوس"],
  ["graduate", "دراسات عليا"],
  ["non-credit", "غير أكاديمي"],
]);

function sha(value) {
  return createHash("sha256").update(value).digest("hex");
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

async function fetchWithRetry(url, options = {}) {
  let lastError;
  for (let attempt = 1; attempt <= 5; attempt += 1) {
    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          accept: "application/json,text/html,application/xhtml+xml",
          "accept-language": "en-US,en;q=0.9",
          "user-agent": USER_AGENT,
          ...options.headers,
        },
        redirect: "follow",
        signal: AbortSignal.timeout(90_000),
      });
      if (response.ok) return response;
      const message = `HTTP ${response.status} while reading ${url}`;
      if (response.status !== 429 && response.status < 500) throw new Error(message);
      lastError = new Error(message);
    } catch (error) {
      lastError = error;
    }
    if (attempt < 5) await delay(attempt * 1_000);
  }
  throw new Error(`Failed to read ${url}: ${lastError?.message ?? "unknown error"}`, {
    cause: lastError,
  });
}

async function fetchJson(url, options = {}) {
  const response = await fetchWithRetry(url, options);
  return response.json();
}

async function fetchHtml(url, options = {}) {
  const response = await fetchWithRetry(url, options);
  return response.text();
}

async function mapLimit(items, concurrency, mapper) {
  const results = new Array(items.length);
  let cursor = 0;
  async function worker() {
    while (cursor < items.length) {
      const index = cursor;
      cursor += 1;
      results[index] = await mapper(items[index], index);
    }
  }
  await Promise.all(
    Array.from({ length: Math.min(concurrency, items.length) }, () => worker()),
  );
  return results;
}

function decodeHtml(value = "") {
  return value
    .replace(/&#x([0-9a-f]+);/giu, (_, code) =>
      String.fromCodePoint(Number.parseInt(code, 16)),
    )
    .replace(/&#([0-9]+);/gu, (_, code) =>
      String.fromCodePoint(Number.parseInt(code, 10)),
    )
    .replace(/&([a-z]+);/giu, (entity, name) =>
      HTML_ENTITIES.get(name.toLowerCase()) ?? entity,
    );
}

function cleanText(value = "") {
  return decodeHtml(
    String(value)
      .replace(/<br\s*\/?>/giu, "\n")
      .replace(/<\/(?:div|li|p|tr|h\d)>/giu, "\n")
      .replace(/<[^>]+>/gu, " "),
  )
    .replace(/\r/gu, "")
    .replace(/[\t ]+/gu, " ")
    .replace(/ *\n */gu, "\n")
    .replace(/\n{2,}/gu, "\n")
    .trim();
}

function inlineText(value = "") {
  return cleanText(value).replace(/\s+/gu, " ").trim();
}

function attribute(attributes, name) {
  const match = attributes.match(
    new RegExp(`${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`, "iu"),
  );
  return decodeHtml(match?.[1] ?? match?.[2] ?? "");
}

function limit(value, maxLength) {
  const normalized = inlineText(value);
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, Math.max(1, maxLength - 1)).trimEnd()}…`;
}

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function asciiSlug(value) {
  return String(value)
    .normalize("NFKD")
    .replace(/\p{Mark}/gu, "")
    .toLowerCase()
    .replace(/&/gu, " and ")
    .replace(/[^a-z0-9]+/gu, "-")
    .replace(/^-+|-+$/gu, "")
    .replace(/-{2,}/gu, "-");
}

function stableSlug(prefix, label, key) {
  const suffix = sha(key).slice(0, 10);
  const available = 190 - prefix.length - suffix.length - 2;
  const body = (asciiSlug(label) || "opportunity")
    .slice(0, available)
    .replace(/-+$/u, "");
  return `${prefix}-${body}-${suffix}`;
}

function httpsUrl(value, fallback = null) {
  if (!value) return fallback;
  try {
    const url = new URL(value, fallback ?? undefined);
    return url.protocol === "https:" ? url.toString() : fallback;
  } catch {
    return fallback;
  }
}

function isoDate(value) {
  const match = String(value ?? "").match(/^(\d{4})-(\d{2})-(\d{2})/u);
  if (!match) return null;
  const normalized = `${match[1]}-${match[2]}-${match[3]}`;
  const date = new Date(`${normalized}T00:00:00Z`);
  return Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 10) !== normalized
    ? null
    : normalized;
}

function parseUsDate(value) {
  const match = String(value ?? "").match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/u);
  if (!match) return null;
  const [, month, day, year] = match;
  return isoDate(
    `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`,
  );
}

function parseEnglishDate(value) {
  const match = String(value ?? "").match(
    /\b(\d{1,2})\s+(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{4})\b/iu,
  );
  if (!match) return null;
  const month = ENGLISH_MONTHS.get(match[2].toLowerCase());
  return isoDate(
    `${match[3]}-${String(month).padStart(2, "0")}-${String(match[1]).padStart(2, "0")}`,
  );
}

function isCurrent(date) {
  return date && date >= TODAY;
}

function arabicRegion(code) {
  if (!code) return "دولي";
  const normalized = { EL: "GR", UK: "GB" }[code] ?? code;
  if (normalized === "XK") return "كوسوفو";
  try {
    return new Intl.DisplayNames(["ar"], { type: "region" }).of(normalized) ?? code;
  } catch {
    return code;
  }
}

function coverImage(key) {
  return `/images/opportunity-${(Number.parseInt(sha(key).slice(0, 2), 16) % 4) + 1}.svg`;
}

function baseRecord({
  externalKey,
  slugPrefix,
  title,
  shortDescription,
  description,
  category,
  organization,
  country,
  funding,
  deadline = null,
  deadlineText,
  statusOverride = null,
  alwaysOpen = false,
  officialUrl,
  applyUrl = officialUrl,
  sourceName,
  sourceUrl,
  specialities,
  nationalities = ["حسب شروط الجهة الرسمية"],
  degreeLevels = ["لا يشترط مستوى أكاديمي محدد"],
}) {
  return {
    externalKey: limit(externalKey, 190),
    slug: stableSlug(slugPrefix, title, `${sourceName}\0${externalKey}`),
    title: limit(title, 300),
    shortDescription: limit(shortDescription, 500),
    description: cleanText(description),
    category,
    organization: limit(organization, 180),
    country: limit(country, 100),
    funding,
    deadline,
    deadlineText: limit(deadlineText, 120),
    statusOverride,
    alwaysOpen,
    featured: false,
    published: true,
    coverImage: coverImage(externalKey),
    officialUrl,
    applyUrl,
    sourceName,
    sourceUrl,
    specialities: unique(specialities).slice(0, 8).map((value) => limit(value, 255)),
    nationalities: unique(nationalities).slice(0, 8).map((value) => limit(value, 255)),
    degreeLevels: unique(degreeLevels).slice(0, 8).map((value) => limit(value, 255)),
  };
}

function europeanYouthUrl(offset, size) {
  const url = new URL("https://youth.europa.eu/api/rest/eyp/v1/search_en");
  url.searchParams.set("type", "Opportunity");
  url.searchParams.set("size", String(size));
  url.searchParams.set("from", String(offset));
  url.searchParams.set("sort", "created:desc");
  url.searchParams.set("filters[status]", "open");
  url.searchParams.set("filters[date_end][operator]", ">=");
  url.searchParams.set("filters[date_end][value]", TODAY);
  url.searchParams.set("filters[date_application_end][operator]", ">=");
  url.searchParams.set("filters[date_application_end][value]", TODAY);
  url.searchParams.set("filters[date_application_end][group]", "deadline");
  url.searchParams.set("filters[date_application_end][type]", "must");
  url.searchParams.set("filters[has_no_deadline][value]", "true");
  url.searchParams.set("filters[has_no_deadline][type]", "must");
  url.searchParams.set("filters[has_no_deadline][group]", "deadline");
  return url;
}

async function loadEuropeanYouthRecords() {
  const pageSize = 100;
  const first = await fetchJson(europeanYouthUrl(0, pageSize));
  const total = Number(first?.hits?.total?.value ?? 0);
  const offsets = Array.from(
    { length: Math.max(0, Math.ceil(total / pageSize) - 1) },
    (_, index) => (index + 1) * pageSize,
  );
  const remaining = await mapLimit(offsets, 2, async (offset) => {
    await delay(150);
    return fetchJson(europeanYouthUrl(offset, pageSize));
  });
  const rows = [first, ...remaining]
    .flatMap((page) => page?.hits?.hits ?? [])
    .map((hit) => hit?._source)
    .filter(Boolean);
  const current = [...new Map(rows.map((row) => [String(row.opid), row])).values()].filter(
    (row) => {
      const end = isoDate(row.date_end);
      const deadline = isoDate(row.date_application_end);
      return (
        row.status === "open" &&
        isCurrent(end) &&
        (row.has_no_deadline === true || isCurrent(deadline))
      );
    },
  );
  if (current.length < 900 || current.length < total * 0.9) {
    throw new Error(
      `European Youth Portal returned ${total} hits but only ${current.length} current unique records`,
    );
  }

  return current.map((row) => {
    const deadline = row.has_no_deadline ? null : isoDate(row.date_application_end);
    const officialUrl = `https://youth.europa.eu/solidarity/opportunity/${row.opid}_en`;
    const topics = unique([...(row.esc_topics ?? []), ...(row.topics ?? [])]).map(
      (topic) => EYP_TOPICS.get(topic) ?? `مجال ${topic}`,
    );
    const start = isoDate(row.date_start);
    const end = isoDate(row.date_end);
    const esc = row.is_esc_related === true;
    return baseRecord({
      externalKey: `eyp-${row.opid}`,
      slugPrefix: "eyp-volunteer",
      title: `فرصة تطوع: ${row.title}`,
      shortDescription: `فرصة تطوع دولية لدى ${row.organisation_name || "جهة مضيفة معتمدة"} في ${arabicRegion(row.country)}، منشورة ومفتوحة في بوابة الشباب الأوروبية.`,
      description: `نبذة\nفرصة تطوع دولية منشورة في بوابة الشباب الأوروبية لدى ${row.organisation_name || "الجهة المضيفة"} في ${arabicRegion(row.country)}.\n\nالفترة والتمويل\n${start && end ? `الفترة المنشورة من ${start} إلى ${end}.` : "راجع فترة النشاط في الإعلان الرسمي."} ${esc ? "الفرصة مرتبطة ببرنامج فيلق التضامن الأوروبي، وتخضع تغطية السفر والسكن والطعام والمصروف والتأمين لقواعد البرنامج والإعلان." : "تختلف التغطية المالية بحسب البرنامج والجهة المضيفة."}\n\nالتقديم\nراجع ملف المشارك والدول المؤهلة ومتطلبات السيرة أو رسالة الدافع في صفحة الفرصة قبل التقديم.`,
      category: "تطوع",
      organization: row.organisation_name || "European Youth Portal host organisation",
      country: arabicRegion(row.country),
      funding: esc ? "FULLY_FUNDED" : "PARTIALLY_FUNDED",
      deadline,
      deadlineText: row.has_no_deadline
        ? "التقديم مستمر ما دامت الفرصة منشورة"
        : `آخر موعد معلن: ${deadline}`,
      alwaysOpen: row.has_no_deadline === true,
      officialUrl,
      sourceName: "European Youth Portal — European Commission",
      sourceUrl: SOURCES.europeanYouth,
      specialities: topics.length > 0 ? topics : ["العمل المجتمعي والتطوع"],
      nationalities: ["المقيمون في الدول المؤهلة وفق الإعلان"],
    });
  });
}

async function grantsGovPage(startRecordNum, rows) {
  return fetchJson("https://api.grants.gov/v1/api/search2", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      rows,
      startRecordNum,
      oppStatuses: "forecasted|posted",
      sortBy: "openDate|desc",
    }),
  });
}

async function loadGrantsGovRecords() {
  const pageSize = 1_000;
  const first = await grantsGovPage(0, pageSize);
  if (first?.errorcode !== 0) throw new Error(`Grants.gov error: ${first?.msg}`);
  const total = Number(first?.data?.hitCount ?? 0);
  const offsets = Array.from(
    { length: Math.max(0, Math.ceil(total / pageSize) - 1) },
    (_, index) => (index + 1) * pageSize,
  );
  const remaining = await mapLimit(offsets, 2, (offset) =>
    grantsGovPage(offset, pageSize),
  );
  const rows = [first, ...remaining].flatMap((page) => page?.data?.oppHits ?? []);
  const current = [...new Map(rows.map((row) => [String(row.id), row])).values()].filter(
    (row) => row.oppStatus === "forecasted" || !parseUsDate(row.closeDate) || isCurrent(parseUsDate(row.closeDate)),
  );
  if (current.length < 1_000 || current.length < total * 0.9) {
    throw new Error(`Grants.gov returned ${total} hits but parsed ${current.length}`);
  }

  return current.map((row) => {
    const deadline = parseUsDate(row.closeDate);
    const forecasted = row.oppStatus === "forecasted";
    const officialUrl = `https://www.grants.gov/search-results-detail/${row.id}`;
    const agency = row.agency || row.agencyName || row.agencyCode || "U.S. federal agency";
    return baseRecord({
      externalKey: `grants-gov-${row.id}`,
      slugPrefix: "grants-gov",
      title: `منحة مالية: ${row.title}`,
      shortDescription: `إعلان تمويل اتحادي أمريكي رقم ${row.number || row.id} صادر عن ${agency}، ومدرج رسميًا في Grants.gov بحالة ${forecasted ? "متوقعة" : "منشورة"}.`,
      description: `نبذة\n${row.title} فرصة تمويل اتحادي مدرجة في Grants.gov تحت الرقم ${row.number || row.id}.\n\nالجهة والأهلية\nالجهة المعلنة هي ${agency}. تختلف الجهات المؤهلة وقيمة الجائزة والمشاركة في التكلفة بحسب نص الإعلان الرسمي، وقد تقتصر بعض الفرص على مؤسسات أمريكية.\n\nالتقديم\nراجع صفحة التفاصيل وحزمة الإعلان الرسمية للتحقق من الأهلية والمبالغ والمرفقات قبل إعداد الطلب.`,
      category: "منح مالية",
      organization: agency,
      country: "الولايات المتحدة / حسب أهلية الإعلان",
      funding: "PARTIALLY_FUNDED",
      deadline,
      deadlineText: deadline
        ? `موعد الإغلاق المنشور: ${deadline}`
        : forecasted
          ? "فرصة متوقعة؛ سيعلن الموعد عند النشر"
          : "الموعد وفق حزمة الإعلان الرسمية",
      statusOverride: deadline ? null : forecasted ? "UPCOMING" : "OPEN",
      officialUrl,
      sourceName: "Grants.gov — U.S. Government",
      sourceUrl: SOURCES.grantsGov,
      specialities: ["تمويل المشاريع والمؤسسات", `الجهة: ${row.agencyCode || agency}`],
      nationalities: ["الجهات المؤهلة وفق الإعلان الرسمي"],
    });
  });
}

function euFundingMultipart(pageNumber) {
  const boundary = `----Jameati${sha(String(pageNumber)).slice(0, 20)}`;
  const query = JSON.stringify({
    bool: {
      must: [
        { terms: { type: ["1"] } },
        { terms: { status: ["31094501", "31094502"] } },
      ],
    },
  });
  const parts = [
    ["query", "application/json", query],
    ["languages", "application/json", '["en"]'],
    ["displayLanguage", "text/plain", "en"],
  ];
  const body = `${parts
    .map(
      ([name, type, value]) =>
        `--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\nContent-Type: ${type}\r\n\r\n${value}\r\n`,
    )
    .join("")}--${boundary}--\r\n`;
  return { boundary, body };
}

async function euFundingPage(pageNumber) {
  const url = new URL(SOURCES.euFundingApi);
  url.searchParams.set("apiKey", "SEDIA");
  url.searchParams.set("text", "***");
  url.searchParams.set("pageSize", "100");
  url.searchParams.set("pageNumber", String(pageNumber));
  url.searchParams.set("sortBy", "deadlineDate");
  url.searchParams.set("orderBy", "ASC");
  const { boundary, body } = euFundingMultipart(pageNumber);
  return fetchJson(url, {
    method: "POST",
    headers: {
      "content-type": `multipart/form-data; boundary=${boundary}`,
      origin: "https://ec.europa.eu",
      referer: "https://ec.europa.eu/info/funding-tenders/opportunities/portal/",
    },
    body,
  });
}

function metadataValue(metadata, field) {
  return Array.isArray(metadata?.[field]) ? metadata[field][0] : null;
}

function euFundingCategory(result) {
  const metadata = result.metadata ?? {};
  const haystack = [
    result.summary,
    metadataValue(metadata, "title"),
    metadataValue(metadata, "callTitle"),
    metadataValue(metadata, "identifier"),
    ...(metadata.typesOfAction ?? []),
  ].join(" ");
  if (/\b(?:prize|award|competition|contest)\b/iu.test(haystack)) return "مسابقات";
  if (/\b(?:fellowships?|post-?doctoral|doctoral|researchers?|msca|erc)\b/iu.test(haystack)) {
    return "زمالات";
  }
  return "منح مالية";
}

async function loadEuFundingRecords() {
  const first = await euFundingPage(1);
  const total = Number(first?.totalResults ?? 0);
  const pageCount = Math.ceil(total / 100);
  const remaining = await mapLimit(
    Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) => index + 2),
    3,
    euFundingPage,
  );
  const rows = [first, ...remaining].flatMap((page) => page?.results ?? []);
  const current = rows
    .map((result) => {
      const deadlines = unique(result.metadata?.deadlineDate ?? [])
        .map(isoDate)
        .filter(isCurrent)
        .sort();
      const status = metadataValue(result.metadata, "status");
      return { result, deadline: deadlines[0] ?? null, status };
    })
    .filter(({ deadline, status }) => deadline || status === "31094501");
  const uniqueRows = [
    ...new Map(
      current.map((row) => [metadataValue(row.result.metadata, "identifier") || row.result.reference, row]),
    ).values(),
  ];
  if (uniqueRows.length < 500) {
    throw new Error(`EU Funding & Tenders returned ${total} hits but parsed ${uniqueRows.length}`);
  }

  return uniqueRows.map(({ result, deadline, status }) => {
    const metadata = result.metadata ?? {};
    const identifier = metadataValue(metadata, "identifier") || result.reference;
    const title =
      metadataValue(metadata, "title") || metadataValue(metadata, "callTitle") || result.summary;
    const category = euFundingCategory(result);
    const officialUrl = `https://ec.europa.eu/info/funding-tenders/opportunities/portal/screen/opportunities/topic-details/${encodeURIComponent(identifier)}`;
    const actions = unique(metadata.typesOfAction ?? []);
    const upcoming = status === "31094501";
    return baseRecord({
      externalKey: `eu-funding-${identifier}`,
      slugPrefix: category === "مسابقات" ? "eu-prize" : category === "زمالات" ? "eu-fellowship" : "eu-grant",
      title: `${category === "مسابقات" ? "جائزة أو مسابقة" : category === "زمالات" ? "فرصة بحث وزمالة" : "منحة مالية أوروبية"}: ${title}`,
      shortDescription: `دعوة ${upcoming ? "قادمة" : "مفتوحة"} في بوابة التمويل والمناقصات التابعة للمفوضية الأوروبية، بالمعرّف ${identifier}.`,
      description: `نبذة\n${title} دعوة رسمية منشورة في بوابة التمويل والمناقصات الأوروبية بالمعرّف ${identifier}.\n\nالتمويل والأهلية\nتحدد وثيقة الموضوع نوع الإجراء والميزانية والبلدان والجهات المؤهلة. غالبية الدعوات مؤسسية أو ائتلافية، بينما تحدد الجوائز والزمالات شروط المتقدمين الخاصة بها.\n\nالتقديم\nراجع شروط الموضوع وملاحقه في البوابة الأوروبية، وأنشئ الطلب من مساحة التقديم الرسمية قبل الموعد المحدد.`,
      category,
      organization: "European Commission — Funding & Tenders Portal",
      country: "الاتحاد الأوروبي / دول مرتبطة مؤهلة",
      funding: category === "مسابقات" ? "PAID" : "PARTIALLY_FUNDED",
      deadline,
      deadlineText: deadline
        ? `موعد الإغلاق التالي: ${deadline}`
        : "دعوة قادمة؛ سيعلن الموعد في البوابة",
      statusOverride: upcoming ? "UPCOMING" : deadline ? null : "OPEN",
      officialUrl,
      sourceName: "EU Funding & Tenders Portal — European Commission",
      sourceUrl: SOURCES.euFunding,
      specialities: actions.length > 0 ? actions : ["التمويل والبحث والابتكار الأوروبي"],
      nationalities: ["الدول والجهات المؤهلة وفق وثيقة الدعوة"],
    });
  });
}

async function loadMitPaged(parameters) {
  const pageSize = 100;
  const makeUrl = (offset) => {
    const url = new URL("https://api.learn.mit.edu/api/v1/learning_resources/");
    url.searchParams.set("limit", String(pageSize));
    url.searchParams.set("offset", String(offset));
    for (const [key, value] of Object.entries(parameters)) {
      url.searchParams.set(key, String(value));
    }
    return url;
  };
  const first = await fetchJson(makeUrl(0));
  const count = Number(first?.count ?? 0);
  const offsets = Array.from(
    { length: Math.max(0, Math.ceil(count / pageSize) - 1) },
    (_, index) => (index + 1) * pageSize,
  );
  const remaining = await mapLimit(offsets, 4, (offset) => fetchJson(makeUrl(offset)));
  return [...new Map([first, ...remaining].flatMap((page) => page?.results ?? []).map((row) => [row.id, row])).values()];
}

function mitTopics(row) {
  const topics = (row.topics ?? []).map((topic) => topic.name).filter(Boolean);
  const departments = (row.departments ?? [])
    .map((department) => department.name)
    .filter(Boolean);
  return unique([...topics, ...departments]).slice(0, 8);
}

function mitDegreeLevels(row) {
  const levels = (row.runs ?? [])
    .flatMap((run) => run.level ?? [])
    .map((level) => level.name ?? level)
    .filter(Boolean)
    .map((level) => DEGREE_LEVELS.get(String(level).toLowerCase()) ?? String(level));
  return unique(levels).length > 0 ? unique(levels) : ["جميع المستويات"].filter(Boolean);
}

function mitOfficialUrl(row) {
  return httpsUrl(row.url) ?? httpsUrl(row.learn_url);
}

async function loadMitOpenCourseRecords() {
  const rows = await loadMitPaged({ resource_type: "course", offered_by: "ocw" });
  const current = rows.filter((row) => row.published === true && mitOfficialUrl(row));
  if (current.length < 2_400) {
    throw new Error(`MIT OpenCourseWare returned only ${current.length} published courses`);
  }
  return current.map((row) => {
    const officialUrl = mitOfficialUrl(row);
    const fields = mitTopics(row);
    return baseRecord({
      externalKey: `mit-ocw-${row.id}`,
      slugPrefix: "mit-ocw",
      title: `دورة MIT المفتوحة: ${row.title}`,
      shortDescription: `مواد مقرر جامعي مجانية ومفتوحة للتعلّم الذاتي من MIT OpenCourseWare${row.coursenum ? `، رقم المقرر ${row.coursenum}` : ""}.`,
      description: `نبذة\n${row.title} مقرر منشور ضمن MIT OpenCourseWare، ويتيح مواد تعليمية مفتوحة للتعلّم الذاتي دون تسجيل أو شهادة أكاديمية.\n\nالوصول والتكلفة\nالمواد متاحة مجانًا عبر الإنترنت. قد تشمل خطة المقرر ومحاضرات وواجبات وقراءات بحسب ما نشره فريق المقرر.\n\nالاستخدام\nافتح صفحة المقرر الرسمية وابدأ من قسم المنهج والمواد المتاحة؛ لا توجد مواعيد بدء أو انتهاء للتعلّم الذاتي.`,
      category: "ورشات",
      organization: "MIT OpenCourseWare",
      country: "عبر الإنترنت",
      funding: "FREE",
      deadlineText: "متاحة للتعلّم الذاتي دون موعد نهائي",
      alwaysOpen: true,
      officialUrl,
      sourceName: "MIT Learn / MIT OpenCourseWare",
      sourceUrl: SOURCES.mitOcw,
      specialities: fields.length > 0 ? fields : ["مقررات جامعية مفتوحة"],
      nationalities: ["جميع الجنسيات"],
      degreeLevels: mitDegreeLevels(row),
    });
  });
}

function futureEnrollmentDeadline(row) {
  return (row.runs ?? [])
    .map((run) => isoDate(run.enrollment_end))
    .filter(isCurrent)
    .sort()[0] ?? null;
}

function mitStatus(row, deadline) {
  if (deadline) return { alwaysOpen: false, statusOverride: null };
  if (row.availability === "anytime") return { alwaysOpen: true, statusOverride: null };
  const nextStart = isoDate(row.next_start_date);
  return {
    alwaysOpen: false,
    statusOverride: isCurrent(nextStart) ? "UPCOMING" : "OPEN",
  };
}

async function loadMitProfessionalRecords() {
  const rows = await loadMitPaged({ resource_type: "course", professional: "true" });
  const current = rows.filter(
    (row) => row.published === true && row.availability !== "archived" && mitOfficialUrl(row),
  );
  if (current.length < 100) {
    throw new Error(`MIT Learn returned only ${current.length} professional courses`);
  }
  return current.map((row) => {
    const officialUrl = mitOfficialUrl(row);
    const deadline = futureEnrollmentDeadline(row);
    const status = mitStatus(row, deadline);
    const provider = row.offered_by?.name || "MIT Professional Education";
    return baseRecord({
      externalKey: `mit-professional-${row.id}`,
      slugPrefix: "mit-training",
      title: `تدريب مهني من MIT: ${row.title}`,
      shortDescription: `دورة مهنية أو تقنية عبر ${provider}، مدرجة في كتالوج MIT Learn الرسمي.`,
      description: `نبذة\n${row.title} دورة مهنية أو تقنية مدرجة في كتالوج MIT Learn وتقدمها ${provider}.\n\nالنمط والتكلفة\nراجع صفحة الدورة لمعرفة نمط الدراسة، المدة، الرسوم، وإمكانية الحصول على شهادة؛ تختلف هذه التفاصيل بين الدورات والدفعات.\n\nالتسجيل\nتحقق من موعد الدفعة الحالية ومتطلبات التسجيل مباشرة في الرابط الرسمي قبل الدفع أو الالتحاق.`,
      category: "تدريب",
      organization: provider,
      country: "عبر الإنترنت / الولايات المتحدة",
      funding: row.free ? "FREE" : "SELF_FUNDED",
      deadline,
      deadlineText: deadline
        ? `نهاية التسجيل المعلنة: ${deadline}`
        : status.alwaysOpen
          ? "متاحة حسب الطلب"
          : "التسجيل بحسب موعد الدفعة في صفحة MIT",
      ...status,
      officialUrl,
      sourceName: "MIT Learn — Professional Learning",
      sourceUrl: SOURCES.mitLearn,
      specialities: mitTopics(row).length > 0 ? mitTopics(row) : ["التدريب المهني والتقني"],
      nationalities: ["جميع الجنسيات؛ راجع قيود التسجيل"],
      degreeLevels: mitDegreeLevels(row),
    });
  });
}

async function loadMitProgramRecords() {
  const rows = await loadMitPaged({ resource_type: "program" });
  const current = rows.filter(
    (row) => row.published === true && row.availability !== "archived" && mitOfficialUrl(row),
  );
  if (current.length < 20) {
    throw new Error(`MIT Learn returned only ${current.length} current programs`);
  }
  return current.map((row) => {
    const officialUrl = mitOfficialUrl(row);
    const deadline = futureEnrollmentDeadline(row);
    const status = mitStatus(row, deadline);
    const provider = row.offered_by?.name || "Massachusetts Institute of Technology";
    return baseRecord({
      externalKey: `mit-program-${row.id}`,
      slugPrefix: "mit-program",
      title: `برنامج جامعي ومهني من MIT: ${row.title}`,
      shortDescription: `برنامج متعدد المقررات أو شهادة مهنية مدرج في كتالوج MIT Learn الرسمي ويقدمه ${provider}.`,
      description: `نبذة\n${row.title} برنامج دراسي متعدد المقررات أو مسار شهادة منشور في كتالوج MIT Learn الرسمي.\n\nالمحتوى والتكلفة\nتختلف مدة البرنامج ورسومه ونوع الاعتماد وعدد مقرراته بحسب الجهة المقدمة. راجع صفحة البرنامج لمعرفة التفاصيل الحالية.\n\nالتسجيل\nتحقق من موعد الدفعة ومتطلبات القبول والدفع في رابط MIT الرسمي قبل التسجيل.`,
      category: "برامج جامعية",
      organization: provider,
      country: "عبر الإنترنت / الولايات المتحدة",
      funding: row.free ? "FREE" : "SELF_FUNDED",
      deadline,
      deadlineText: deadline
        ? `نهاية التسجيل المعلنة: ${deadline}`
        : status.alwaysOpen
          ? "متاح حسب الطلب"
          : "التسجيل بحسب موعد الدفعة في صفحة MIT",
      ...status,
      officialUrl,
      sourceName: "MIT Learn — Programs",
      sourceUrl: SOURCES.mitLearn,
      specialities: mitTopics(row).length > 0 ? mitTopics(row) : ["برامج جامعية ومهنية"],
      nationalities: ["جميع الجنسيات؛ راجع قيود التسجيل"],
      degreeLevels: mitDegreeLevels(row),
    });
  });
}

function parcoursupDegreeLevel(row) {
  const value = [...(row.tf ?? []), ...(row.fl ?? []), ...(row.nm ?? [])].join(" ");
  if (/\b(?:BTS|BTSA|BTSM)\b/iu.test(value)) return "دبلوم تقني عالٍ";
  if (/\b(?:BUT|Bachelor|Licence)\b/iu.test(value)) return "بكالوريوس";
  if (/\bCPGE\b/iu.test(value)) return "برنامج تحضيري جامعي";
  if (/\b(?:Master|Mastère)\b/iu.test(value)) return "ماجستير";
  return "تعليم عالٍ بعد الثانوية";
}

async function parcoursupExport() {
  const url = new URL(
    "https://data.enseignementsup-recherche.gouv.fr/api/explore/v2.1/catalog/datasets/fr-esr-cartographie_formations_parcoursup/exports/json",
  );
  url.searchParams.set("where", 'annee="2026"');
  return fetchJson(url);
}

async function loadParcoursupRecords() {
  if (!Number.isInteger(PARCOURSUP_LIMIT) || PARCOURSUP_LIMIT < 100) {
    throw new Error("JAMEATI_PARCOURSUP_LIMIT must be an integer of at least 100");
  }
  const rows = await parcoursupExport();
  if (!Array.isArray(rows)) throw new Error("Parcoursup export did not return an array");
  const allPrograms = [
    ...new Map(rows.map((row) => [String(row.gta || row.gti || row.code_formation), row])).values(),
  ]
    .filter((row) => httpsUrl(row.fiche))
    .sort((left, right) => String(left.gta).localeCompare(String(right.gta)));
  const target = Math.min(allPrograms.length, PARCOURSUP_LIMIT);
  const current = Array.from({ length: target }, (_, index) =>
    allPrograms[Math.floor((index * allPrograms.length) / target)],
  );
  if (allPrograms.length < 20_000 || current.length !== target) {
    throw new Error(
      `Parcoursup export contained ${allPrograms.length} programs; target ${target}`,
    );
  }
  return current.map((row) => {
    const key = String(row.gta || row.gti || row.code_formation);
    const program = row.fl?.[0] || row.nm?.[0] || row.nmc || "Formation post-bac";
    const type = row.tf?.[0] || parcoursupDegreeLevel(row);
    const officialUrl = httpsUrl(row.fiche);
    const delivery = row.amg?.[0] || "حسب ملف البرنامج";
    return baseRecord({
      externalKey: `parcoursup-2026-${key}`,
      slugPrefix: "parcoursup-program",
      title: `برنامج جامعي في فرنسا: ${program}`,
      shortDescription: `${type} لدى ${row.etab_nom} في ${row.commune || row.region || "فرنسا"}، مدرج في بيانات Parcoursup الرسمية لدورة 2026.`,
      description: `نبذة\n${program} برنامج تعليم عالٍ لدى ${row.etab_nom}، ومدرج في الخريطة الرسمية لبرامج Parcoursup لسنة 2026.\n\nالمكان والنمط\nالمدينة: ${row.commune || "راجع ملف البرنامج"}. المنطقة: ${row.region || "فرنسا"}. نمط الدراسة المنشور: ${delivery}.\n\nالتقديم\nراجع ملف البرنامج ورزنامة Parcoursup للتأكد من شروط القبول وترتيب الرغبات والمراحل التي ما زالت تستقبل طلبات؛ إدراج البرنامج لا يعني توفر مقعد في كل مرحلة.`,
      category: "برامج جامعية",
      organization: row.etab_nom,
      country: row.region ? `فرنسا — ${row.region}` : "فرنسا",
      funding: "SELF_FUNDED",
      deadlineText: "المواعيد وفق رزنامة Parcoursup ومرحلة القبول الحالية",
      statusOverride: "UPCOMING",
      officialUrl,
      sourceName: "Parcoursup — French Ministry of Higher Education",
      sourceUrl: SOURCES.parcoursup,
      specialities: unique([...(row.fl ?? []), ...(row.nm ?? []), type]),
      nationalities: ["المتقدمون المؤهلون عبر Parcoursup"],
      degreeLevels: [parcoursupDegreeLevel(row)],
    });
  });
}

async function mitEventsPage(page) {
  const url = new URL("https://calendar.mit.edu/api/2/events");
  url.searchParams.set("start", TODAY);
  const end = new Date(`${TODAY}T00:00:00Z`);
  end.setUTCFullYear(end.getUTCFullYear() + 1);
  url.searchParams.set("end", end.toISOString().slice(0, 10));
  url.searchParams.set("pp", "100");
  url.searchParams.set("page", String(page));
  return fetchJson(url);
}

function mitEventCategory(event) {
  const types = event.filters?.event_types ?? [];
  const text = types.map((type) => type.name).join(" ");
  return /workshop|class|training|fair/iu.test(text) ? "ورشات" : "مؤتمرات";
}

async function loadMitEventRecords() {
  const first = await mitEventsPage(1);
  const totalPages = Number(first?.page?.total ?? 0);
  const remaining = await mapLimit(
    Array.from({ length: Math.max(0, totalPages - 1) }, (_, index) => index + 2),
    4,
    mitEventsPage,
  );
  const rows = [first, ...remaining]
    .flatMap((page) => page?.events ?? [])
    .map((wrapper) => wrapper.event)
    .filter(Boolean);
  const current = [...new Map(rows.map((event) => [String(event.id), event])).values()].filter(
    (event) => event.publish_status === "published" && !event.private && httpsUrl(event.localist_url),
  );
  if (current.length < 300) {
    throw new Error(`MIT Events returned only ${current.length} current events`);
  }
  return current.map((event) => {
    const dates = (event.event_instances ?? [])
      .map((wrapper) => isoDate(wrapper.event_instance?.start))
      .filter(isCurrent)
      .sort();
    const deadline = dates[0] ?? isoDate(event.first_date);
    if (!isCurrent(deadline)) return null;
    const category = mitEventCategory(event);
    const officialUrl = httpsUrl(event.localist_url);
    const applyUrl = httpsUrl(event.ticket_url) ?? officialUrl;
    const fields = unique([
      ...(event.filters?.event_events_by_interest ?? []).map((item) => item.name),
      ...(event.filters?.event_types ?? []).map((item) => item.name),
      ...(event.keywords ?? []),
    ]);
    const organization = event.departments?.[0]?.name || "MIT Events";
    const online = event.experience === "virtual" || /online|zoom/iu.test(event.location ?? "");
    return baseRecord({
      externalKey: `mit-event-${event.id}`,
      slugPrefix: category === "ورشات" ? "mit-workshop" : "mit-event",
      title: `${category === "ورشات" ? "ورشة أو فعالية" : "فعالية أو مؤتمر"} في MIT: ${event.title}`,
      shortDescription: `فعالية ${online ? "عبر الإنترنت" : "منشورة في تقويم MIT الرسمي"} ينظمها ${organization} بتاريخ ${deadline}.`,
      description: `نبذة\n${event.title} فعالية منشورة في تقويم MIT الرسمي وينظمها ${organization}.\n\nالموعد والمكان\nالموعد التالي المدرج هو ${deadline}. ${online ? "الفعالية عبر الإنترنت؛ راجع رابط البث أو التسجيل." : `المكان المنشور: ${event.location_name || event.location || event.address || "حرم MIT أو المكان المحدد في الإعلان"}.`}\n\nالتسجيل\nراجع صفحة الفعالية لمعرفة الجمهور المستهدف ورسوم الحضور — إن وجدت — وأي تحديث على الوقت أو المكان.`,
      category,
      organization,
      country: online ? "عبر الإنترنت" : "الولايات المتحدة",
      funding: event.free ? "FREE" : "SELF_FUNDED",
      deadline,
      deadlineText: `موعد الفعالية التالي: ${deadline}`,
      officialUrl,
      applyUrl,
      sourceName: "MIT Events Calendar",
      sourceUrl: SOURCES.mitEvents,
      specialities: fields.length > 0 ? fields : ["فعاليات أكاديمية ومهنية"],
      nationalities: ["مفتوحة حسب شروط الفعالية"],
    });
  }).filter(Boolean);
}

function parseSaltoRecords(html) {
  return html
    .split(/<li class="result-container clearfix">/giu)
    .slice(1)
    .map((block) => {
      const category = inlineText(
        /<span class="h3 tool-item-category">([\s\S]*?)<\/span>/iu.exec(block)?.[1] ?? "",
      );
      const titleLink = /<h2 class="tool-item-name"[^>]*>\s*<a\b([^>]*)>([\s\S]*?)<\/a>/iu.exec(
        block,
      );
      const officialUrl = httpsUrl(attribute(titleLink?.[1] ?? "", "href"));
      const title = inlineText(titleLink?.[2] ?? "");
      const afterTitle = block.slice((titleLink?.index ?? 0) + (titleLink?.[0]?.length ?? 0));
      const overview = afterTitle.match(
        /<p class="h5">([\s\S]*?)<\/p>\s*<p class="microcopy mrgn-btm-17">([\s\S]*?)<\/p>/iu,
      );
      const deadlineText = inlineText(
        /Application deadline[\s\S]*?<p class="h3 mrgn-btm-2">([\s\S]*?)<\/p>/iu.exec(block)?.[1] ?? "",
      );
      if (!category || !officialUrl || !title || !deadlineText) return null;
      return {
        activityType: category,
        title,
        officialUrl,
        activityDates: inlineText(overview?.[1] ?? ""),
        location: inlineText(overview?.[2] ?? "دولي"),
        deadline: parseEnglishDate(deadlineText),
      };
    })
    .filter(Boolean);
}

function saltoCategory(activityType) {
  if (/conference|symposium|forum/iu.test(activityType)) return "مؤتمرات";
  if (/study visit|youth exchange|partnership-building/iu.test(activityType)) {
    return "تبادل ثقافي";
  }
  return "ورشات";
}

async function loadSaltoRecords() {
  const [year, month, day] = TODAY.split("-").map(Number);
  const url = new URL(SOURCES.salto);
  const parameters = {
    b_offset: 0,
    b_limit: 500,
    b_order: "applicationDeadline",
    b_begin_date_after_day: day,
    b_begin_date_after_month: month,
    b_begin_date_after_year: year,
    b_application_deadline_after_day: day,
    b_application_deadline_after_month: month,
    b_application_deadline_after_year: year,
  };
  for (const [key, value] of Object.entries(parameters)) {
    url.searchParams.set(key, String(value));
  }
  const rows = parseSaltoRecords(await fetchHtml(url)).filter((row) => isCurrent(row.deadline));
  if (rows.length < 50) throw new Error(`SALTO parsed only ${rows.length} current offers`);
  return rows.map((row) => {
    const category = saltoCategory(row.activityType);
    const key = row.officialUrl.match(/\.(\d+)\/$/u)?.[1] ?? sha(row.officialUrl).slice(0, 16);
    return baseRecord({
      externalKey: `salto-${key}`,
      slugPrefix: category === "تبادل ثقافي" ? "salto-exchange" : category === "مؤتمرات" ? "salto-conference" : "salto-training",
      title: `فرصة SALTO: ${row.title}`,
      shortDescription: `${row.activityType} دولية مدرجة في تقويم SALTO-YOUTH الأوروبي، تقام في ${row.location}.`,
      description: `نبذة\n${row.title} نشاط دولي من نوع ${row.activityType} مدرج في تقويم التدريب الأوروبي SALTO-YOUTH.\n\nالموعد والمكان\nموعد النشاط المنشور: ${row.activityDates || "راجع الإعلان"}. المكان: ${row.location}.\n\nالأهلية والتقديم\nراجع قائمة دول المشاركين والجمهور المستهدف والتمويل ورسوم المشاركة في صفحة النشاط، ثم استخدم آلية التقديم التي حددها المنظم.`,
      category,
      organization: "SALTO-YOUTH European Training Calendar",
      country: row.location,
      funding: "PARTIALLY_FUNDED",
      deadline: row.deadline,
      deadlineText: `آخر موعد للتقديم: ${row.deadline}`,
      officialUrl: row.officialUrl,
      sourceName: "SALTO-YOUTH European Training Calendar",
      sourceUrl: SOURCES.salto,
      specialities: [row.activityType, "العمل الشبابي والتعلم غير الرسمي"],
      nationalities: ["الدول المدرجة في إعلان SALTO"],
    });
  });
}

function parseEuraxessPage(html) {
  return html
    .split(/<div class="label-thumbnail-wrapper[^>]*>/giu)
    .slice(1)
    .map((block) => {
      const article = block.split(/<\/article>/iu)[0] ?? block;
      const titleLink = /<h3 class="ecl-content-block__title">\s*<a\b([^>]*)>([\s\S]*?)<\/a>/iu.exec(
        article,
      );
      const href = attribute(titleLink?.[1] ?? "", "href");
      const officialUrl = httpsUrl(href, SOURCES.euraxess);
      const title = inlineText(titleLink?.[2] ?? "");
      const country = inlineText(
        /<span\s+class="ecl-label ecl-label--highlight"[^>]*>([\s\S]*?)<\/span>/iu.exec(
          article,
        )?.[1] ?? "دولي",
      );
      const primaryMeta =
        /<ul class="ecl-content-block__primary-meta-container">([\s\S]*?)<\/ul>/iu.exec(
          article,
        )?.[1] ?? "";
      const organization = inlineText(/<a\b[^>]*>([\s\S]*?)<\/a>/iu.exec(primaryMeta)?.[1] ?? "EURAXESS");
      const deadlineLabel = inlineText(
        /Deadline:[\s\S]*?<div class="ecl-text-standard[^>]*>\s*([^<]+?)\s*<\/div>/iu.exec(
          article,
        )?.[1] ?? "",
      );
      const fields = unique(
        [...article.matchAll(/aria-label="Filter by ([^"]+)"/giu)]
          .map((match) => decodeHtml(match[1]))
          .filter((value) => !/funded|programme/iu.test(value)),
      );
      if (!officialUrl || !title || !deadlineLabel) return null;
      return {
        officialUrl,
        title,
        organization,
        country,
        deadline: parseEnglishDate(deadlineLabel),
        deadlineLabel,
        fields,
      };
    })
    .filter(Boolean);
}

async function loadEuraxessRecords() {
  const makeUrl = (page) => {
    const url = new URL(SOURCES.euraxess);
    url.searchParams.append("f[0]", "offer_type:funding");
    url.searchParams.set("sort[name]", "field_job_application_deadline_z");
    url.searchParams.set("sort[direction]", "ASC");
    url.searchParams.set("page", String(page));
    return url;
  };
  const firstHtml = await fetchHtml(makeUrl(0));
  const totalMatch = firstHtml.match(/Search results\s*<span>\s*\((\d+)\)<\/span>/iu);
  if (!totalMatch) throw new Error("Could not discover EURAXESS funding total");
  const total = Number.parseInt(totalMatch[1], 10);
  const pageCount = Math.ceil(total / 10);
  const remaining = await mapLimit(
    Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) => index + 1),
    3,
    async (page) => parseEuraxessPage(await fetchHtml(makeUrl(page))),
  );
  const rows = [parseEuraxessPage(firstHtml), ...remaining]
    .flat()
    .filter((row) => !row.deadline || isCurrent(row.deadline));
  const uniqueRows = [...new Map(rows.map((row) => [row.officialUrl, row])).values()];
  if (uniqueRows.length < 100 || uniqueRows.length < total * 0.8) {
    throw new Error(`EURAXESS reported ${total} records but parsed ${uniqueRows.length}`);
  }
  return uniqueRows.map((row) => {
    const key = sha(row.officialUrl).slice(0, 20);
    return baseRecord({
      externalKey: `euraxess-funding-${key}`,
      slugPrefix: "euraxess-research",
      title: `زمالة أو تمويل بحثي: ${row.title}`,
      shortDescription: `فرصة تمويل أو زمالة بحثية لدى ${row.organization} في ${row.country}، منشورة في بوابة EURAXESS التابعة للمفوضية الأوروبية.`,
      description: `نبذة\n${row.title} فرصة تمويل أو بحث منشورة في EURAXESS لدى ${row.organization}.\n\nالمجال والأهلية\nتتفاوت الفرص بين الزمالات والمنح البحثية ومشروعات الدكتوراه والاستضافة. راجع الإعلان لمعرفة ملف الباحث والمؤهل والبلدان المقبولة وقيمة التمويل.\n\nالتقديم\nالموعد المنشور هو ${row.deadline || row.deadlineLabel}. اقرأ المستندات ومتطلبات الجهة المضيفة في صفحة EURAXESS قبل إرسال الطلب.`,
      category: "زمالات",
      organization: row.organization,
      country: row.country,
      funding: "PARTIALLY_FUNDED",
      deadline: row.deadline,
      deadlineText: row.deadline
        ? `آخر موعد للتقديم: ${row.deadline}`
        : `الموعد وفق إعلان EURAXESS: ${row.deadlineLabel}`,
      statusOverride: row.deadline ? null : "OPEN",
      officialUrl: row.officialUrl,
      sourceName: "EURAXESS — European Commission",
      sourceUrl: SOURCES.euraxess,
      specialities: row.fields.length > 0 ? row.fields : ["البحث العلمي والزمالات"],
      nationalities: ["حسب أهلية إعلان EURAXESS"],
      degreeLevels: ["باحثون وطلبة دراسات عليا"],
    });
  });
}

function validateRecord(record) {
  const requiredStrings = [
    "externalKey",
    "slug",
    "title",
    "shortDescription",
    "description",
    "category",
    "organization",
    "country",
    "funding",
    "deadlineText",
    "coverImage",
    "officialUrl",
    "applyUrl",
    "sourceName",
    "sourceUrl",
  ];
  for (const field of requiredStrings) {
    if (typeof record[field] !== "string" || !record[field].trim()) {
      throw new Error(`${field} is missing for ${record.externalKey}`);
    }
  }
  const lengths = {
    externalKey: 190,
    slug: 190,
    title: 300,
    shortDescription: 500,
    category: 100,
    organization: 180,
    country: 100,
    deadlineText: 120,
    coverImage: 500,
    officialUrl: 500,
    applyUrl: 500,
    sourceName: 180,
    sourceUrl: 500,
  };
  for (const [field, maxLength] of Object.entries(lengths)) {
    if (record[field].length > maxLength) {
      throw new Error(`${field} is too long for ${record.externalKey}`);
    }
  }
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/u.test(record.slug)) {
    throw new Error(`Invalid slug ${record.slug}`);
  }
  for (const field of ["officialUrl", "applyUrl", "sourceUrl"]) {
    if (new URL(record[field]).protocol !== "https:") {
      throw new Error(`${field} is not HTTPS for ${record.externalKey}`);
    }
  }
  if (record.alwaysOpen && (record.deadline || record.statusOverride)) {
    throw new Error(`alwaysOpen conflicts with date/status for ${record.externalKey}`);
  }
  if (!record.deadline && !record.alwaysOpen && !record.deadlineText) {
    throw new Error(`No deadline information for ${record.externalKey}`);
  }
  for (const field of ["specialities", "nationalities", "degreeLevels"]) {
    if (!Array.isArray(record[field]) || record[field].length === 0) {
      throw new Error(`${field} is empty for ${record.externalKey}`);
    }
    if (record[field].some((value) => !value || value.length > 255)) {
      throw new Error(`${field} has an invalid value for ${record.externalKey}`);
    }
  }
}

async function existingRecords() {
  const paths = ["official-opportunities.json", "official-scholarships.json"];
  const feeds = await Promise.all(
    paths.map(async (file) => JSON.parse(await readFile(resolve(DATA_DIRECTORY, file), "utf8"))),
  );
  return feeds.flatMap((feed) => feed.records ?? []);
}

function deduplicate(records, existing) {
  const identities = new Set(
    existing.map((record) => `${record.sourceName}\0${record.externalKey}`),
  );
  const slugs = new Set(existing.map((record) => record.slug.toLowerCase()));
  const urls = new Set(existing.map((record) => record.officialUrl.toLowerCase()));
  const accepted = [];
  const skipped = [];
  for (const record of records) {
    validateRecord(record);
    const identity = `${record.sourceName}\0${record.externalKey}`;
    const slug = record.slug.toLowerCase();
    const url = record.officialUrl.toLowerCase();
    if (identities.has(identity) || slugs.has(slug) || urls.has(url)) {
      skipped.push(record.externalKey);
      continue;
    }
    identities.add(identity);
    slugs.add(slug);
    urls.add(url);
    accepted.push(record);
  }
  return { accepted, skipped };
}

function countsBy(records, selector) {
  return Object.fromEntries(
    [...records.reduce((counts, record) => {
      const key = selector(record);
      counts.set(key, (counts.get(key) ?? 0) + 1);
      return counts;
    }, new Map())].sort(([left], [right]) => left.localeCompare(right)),
  );
}

async function main() {
  const sourceLoaders = [
    ["European Youth Portal", loadEuropeanYouthRecords],
    ["Grants.gov", loadGrantsGovRecords],
    ["EU Funding & Tenders", loadEuFundingRecords],
    ["MIT OpenCourseWare", loadMitOpenCourseRecords],
    ["MIT professional learning", loadMitProfessionalRecords],
    ["MIT programs", loadMitProgramRecords],
    ["Parcoursup programs", loadParcoursupRecords],
    ["MIT events", loadMitEventRecords],
    ["SALTO-YOUTH", loadSaltoRecords],
    ["EURAXESS", loadEuraxessRecords],
  ];
  const loaded = await Promise.all(
    sourceLoaders.map(async ([name, loader]) => {
      const records = await loader();
      process.stdout.write(`${name}: ${records.length}\n`);
      return records;
    }),
  );
  const { accepted, skipped } = deduplicate(loaded.flat(), await existingRecords());
  const requiredCategories = [
    "تطوع",
    "مسابقات",
    "برامج جامعية",
    "زمالات",
    "منح مالية",
    "تدريب",
    "تبادل ثقافي",
    "ورشات",
    "مؤتمرات",
  ];
  const categoryCounts = countsBy(accepted, (record) => record.category);
  for (const category of requiredCategories) {
    if (!categoryCounts[category]) throw new Error(`No records generated for ${category}`);
  }
  const feed = {
    version: `official-expanded-${TODAY}.1`,
    verifiedAt: new Date().toISOString(),
    verifiedBy: "فريق تحرير جامعتي — بيانات وصفية من عشرة كتالوجات رسمية",
    records: accepted,
  };
  await writeFile(OUTPUT, `${JSON.stringify(feed, null, 2)}\n`, "utf8");
  process.stdout.write(
    `Wrote ${accepted.length} records to ${OUTPUT}\n` +
      `Skipped as duplicates with existing/expanded feeds: ${skipped.length}\n` +
      `Categories: ${JSON.stringify(categoryCounts)}\n` +
      `Sources: ${JSON.stringify(countsBy(accepted, (record) => record.sourceName))}\n`,
  );
}

await main();
