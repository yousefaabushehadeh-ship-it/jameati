import { createHash } from "node:crypto";
import { createWriteStream } from "node:fs";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";

const SCRIPT_DIRECTORY = dirname(fileURLToPath(import.meta.url));
const DATA_DIRECTORY = resolve(SCRIPT_DIRECTORY, "../src/main/resources/data");
const CORE_CATALOG = resolve(DATA_DIRECTORY, "official-catalog.json");
const OUTPUT = resolve(DATA_DIRECTORY, "official-expanded-catalog.json");
const TODAY = process.env.JAMEATI_SNAPSHOT_DATE ?? new Date().toISOString().slice(0, 10);
const USER_AGENT =
  "JameatiCatalogMetadataSync/1.0 (public metadata only; https://jamieati.org; contact@jamieati.org)";

const LIMITS = {
  articles: positiveInteger("JAMEATI_OPENALEX_ARTICLE_LIMIT", 2_600),
  organizations: positiveInteger("JAMEATI_ROR_ORGANIZATION_LIMIT", 5_000),
  universities: positiveInteger("JAMEATI_ROR_UNIVERSITY_LIMIT", 5_000),
  arbeitnowPages: positiveInteger("JAMEATI_ARBEITNOW_PAGES", 10),
};

const SOURCES = {
  arbeitnowApi: "https://www.arbeitnow.com/api/job-board-api",
  arbeitnowDocs: "https://www.arbeitnow.com/blog/job-board-api",
  jobicyApi: "https://jobicy.com/api/v2/remote-jobs?count=200",
  jobicyDocs: "https://jobicy.com/jobs-rss-feed",
  remoteOkApi: "https://remoteok.com/api",
  remotiveApi: "https://remotive.com/api/remote-jobs",
  remotiveDocs: "https://remotive.com/remote-jobs/api",
  mitApi:
    "https://api.learn.mit.edu/api/v1/learning_resources/?limit=100&offset=0&resource_type=course&offered_by=ocw",
  mitOcw: "https://ocw.mit.edu/",
  microsoftCatalog: "https://learn.microsoft.com/api/catalog/?locale=en-us",
  microsoftLearn: "https://learn.microsoft.com/training/",
  openAlexWorks: "https://api.openalex.org/works",
  openAlexTopics: "https://api.openalex.org/topics",
  openAlexDocs: "https://help.openalex.org/",
  rorRecord: "https://zenodo.org/api/records/6347574",
  ror: "https://ror.org/",
};

const FREELANCE_PORTALS = [
  {
    key: "upwork",
    title: "Upwork — بوابة العمل الحر",
    organization: "Upwork",
    country: "عالمي",
    url: "https://www.upwork.com/",
  },
  {
    key: "fiverr",
    title: "Fiverr — سوق الخدمات والعمل الحر",
    organization: "Fiverr",
    country: "عالمي",
    url: "https://www.fiverr.com/",
  },
  {
    key: "freelancer",
    title: "Freelancer — بوابة المشاريع المستقلة",
    organization: "Freelancer",
    country: "عالمي",
    url: "https://www.freelancer.com/",
  },
  {
    key: "mostaql",
    title: "مستقل — منصة المشاريع العربية",
    organization: "مستقل",
    country: "العالم العربي",
    url: "https://mostaql.com/",
  },
  {
    key: "khamsat",
    title: "خمسات — سوق الخدمات المصغرة",
    organization: "خمسات",
    country: "العالم العربي",
    url: "https://khamsat.com/",
  },
];

const HTML_ENTITIES = new Map([
  ["amp", "&"],
  ["apos", "'"],
  ["gt", ">"],
  ["hellip", "…"],
  ["lt", "<"],
  ["nbsp", " "],
  ["quot", '"'],
]);

const COURSE_CATEGORY_RULES = [
  ["تصميم وإبداع", /\b(?:design|creative|ux|ui|graphic|visual|animation|arts?|music|media)\b/iu],
  ["لغات", /\b(?:languages?|english|arabic|french|spanish|german|communication|writing|literature)\b/iu],
  ["أعمال وإدارة", /\b(?:business|management|manager|marketing|finance|sales|entrepreneurship|leadership|projects?|accounting|commerce|customer)\b/iu],
  ["تقنية وبرمجة", /\b(?:software|programming|computer|data|cloud|azure|github|machine learning|artificial intelligence|security|developer|database|web|network|python|java|technology)\b|\.net/iu],
];

const JOB_CATEGORY_RULES = [
  ["تصميم", /\b(?:design|designer|creative|ux|ui|graphic|animation|video)\b/iu],
  ["تعليم", /\b(?:teacher|teaching|education|instructor|professor|academic|tutor|learning)\b/iu],
  ["تسويق", /\b(?:marketing|sales|growth|seo|content|social media|account executive)\b/iu],
  ["إدارة", /\b(?:manager|management|director|operations|human resources|recruiter|finance|accounting|product owner|project)\b/iu],
  ["تقنية", /\b(?:software|developer|engineer|data|cloud|security|devops|qa|frontend|backend|full.?stack|programmer|technical|technology|machine learning|artificial intelligence)\b/iu],
];

const OPENALEX_DOMAIN_ARABIC = new Map([
  ["Life Sciences", "علوم الحياة"],
  ["Health Sciences", "العلوم الصحية"],
  ["Physical Sciences", "العلوم الفيزيائية"],
  ["Social Sciences", "العلوم الاجتماعية"],
]);

const ARABIC_REGIONS = new Intl.DisplayNames(["ar"], { type: "region" });

function positiveInteger(name, fallback) {
  const parsed = Number.parseInt(process.env[name] ?? String(fallback), 10);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${name} must be a positive integer`);
  }
  return parsed;
}

function sha(value) {
  return createHash("sha256").update(String(value)).digest("hex");
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

async function fetchWithRetry(url, options = {}) {
  let lastError;
  for (let attempt = 1; attempt <= 7; attempt += 1) {
    try {
      const response = await fetch(url, {
        ...options,
        headers: {
          accept: "application/json,application/zip;q=0.9,*/*;q=0.8",
          "accept-language": "en-US,en;q=0.9",
          "user-agent": USER_AGENT,
          ...options.headers,
        },
        redirect: "follow",
        signal: AbortSignal.timeout(120_000),
      });
      if (response.ok) return response;
      const message = `HTTP ${response.status} while reading ${url}`;
      if (response.status !== 429 && response.status < 500) throw new Error(message);
      const retryAfter = Number.parseInt(response.headers.get("retry-after") ?? "", 10);
      lastError = Object.assign(new Error(message), {
        retryAfter: Number.isFinite(retryAfter) ? retryAfter * 1_000 : undefined,
      });
    } catch (error) {
      lastError = error;
    }
    if (attempt < 7) {
      const wait = Math.min(lastError?.retryAfter ?? 750 * 2 ** (attempt - 1), 60_000);
      await delay(wait);
    }
  }
  throw new Error(`Failed to read ${url}: ${lastError?.message ?? "unknown error"}`, {
    cause: lastError,
  });
}

async function fetchJson(url, options = {}) {
  const response = await fetchWithRetry(url, options);
  return response.json();
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
  return String(value)
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

function repairMojibake(value = "") {
  const text = String(value);
  if (!/[ÃÂâð]/u.test(text)) return text;
  try {
    const repaired = Buffer.from(text, "latin1").toString("utf8");
    return repaired.includes("�") ? text : repaired;
  } catch {
    return text;
  }
}

function inlineText(value = "") {
  return repairMojibake(decodeHtml(value))
    .replace(/<[^>]+>/gu, " ")
    .replace(/[\u0000-\u001f\u007f]/gu, " ")
    .replace(/\s+/gu, " ")
    .trim();
}

function limit(value, maxLength) {
  const normalized = inlineText(value);
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, Math.max(1, maxLength - 1)).trimEnd()}…`;
}

function array(value) {
  return Array.isArray(value) ? value : [];
}

function unique(values) {
  return [...new Set(
    values
      .filter((value) => value !== null && value !== undefined && value !== "")
      .map((value) => inlineText(value))
      .filter(Boolean),
  )];
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
  const body = (asciiSlug(label) || "record")
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
  if (typeof value === "number" && Number.isFinite(value)) {
    return new Date(value * 1_000).toISOString().slice(0, 10);
  }
  const match = String(value ?? "").match(/^(\d{4})-(\d{2})-(\d{2})/u);
  if (!match) return null;
  const normalized = `${match[1]}-${match[2]}-${match[3]}`;
  const date = new Date(`${normalized}T00:00:00Z`);
  return Number.isNaN(date.valueOf()) || date.toISOString().slice(0, 10) !== normalized
    ? null
    : normalized;
}

function sourceFingerprint(record) {
  return sha(JSON.stringify([
    record.type,
    record.sourceExternalKey,
    record.title,
    record.summary,
    record.content,
    record.category,
    record.organization,
    record.country,
    record.website,
    record.published,
    record.meta,
    record.attributes,
  ]));
}

function catalogRecord({
  type,
  externalKey,
  slugPrefix,
  title,
  summary,
  content,
  category,
  organization,
  country,
  website,
  meta,
  sourceName,
  sourceUrl,
  officialUrl = website,
  license = null,
  attributes = {},
}) {
  const cleanTitle = limit(title, 300);
  const cleanOrganization = organization ? limit(organization, 180) : null;
  const secureWebsite = httpsUrl(website);
  const secureSourceUrl = httpsUrl(sourceUrl);
  const secureOfficialUrl = httpsUrl(officialUrl);
  const record = {
    type,
    sourceExternalKey: limit(externalKey, 190),
    slug: stableSlug(slugPrefix, cleanTitle, `${type}:${externalKey}`),
    title: cleanTitle,
    summary: limit(summary, 500),
    content: inlineText(content),
    category: limit(category, 100),
    organization: cleanOrganization,
    country: country ? limit(country, 100) : null,
    image: null,
    website: secureWebsite,
    featured: false,
    published: true,
    rating: null,
    meta: meta ? limit(meta, 80) : null,
    sourceName: limit(sourceName, 180),
    sourceUrl: secureSourceUrl,
    officialUrl: secureOfficialUrl,
    verifiedAt: TODAY,
    license,
    attributes,
  };
  record.sourceFingerprint = sourceFingerprint(record);
  return record;
}

function categoryByRules(text, rules, fallback) {
  return rules.find(([, pattern]) => pattern.test(text))?.[0] ?? fallback;
}

function courseCategory(item) {
  const haystack = [
    item.title,
    ...array(item.subjects),
    ...array(item.roles),
    ...array(item.products),
    ...array(item.topics).map((topic) => topic?.name),
    ...array(item.ocw_topics),
  ].join(" ");
  return categoryByRules(haystack, COURSE_CATEGORY_RULES, "تطوير مهني");
}

function jobCategory(title, tags = []) {
  return categoryByRules(`${title} ${array(tags).join(" ")}`, JOB_CATEGORY_RULES, "وظائف متنوعة");
}

function articleCategory(topic) {
  const hierarchy = `${topic?.display_name ?? ""} ${topic?.field?.display_name ?? ""} ${topic?.domain?.display_name ?? ""}`;
  if (/medicine|health|nursing|dentistry|veterinary|neuroscience|pharmacol|immunology|microbiology|biochemistry|genetics|biological/iu.test(hierarchy)) {
    return "علوم صحية وحيوية";
  }
  if (/computer|engineering|technology|decision sciences/iu.test(hierarchy)) {
    return "هندسة وتقنية";
  }
  if (/business|management|accounting|economics|finance/iu.test(hierarchy)) {
    return "أعمال واقتصاد";
  }
  if (/social sciences|arts and humanities|psychology/iu.test(hierarchy)) {
    return "علوم اجتماعية وإنسانية";
  }
  return "علوم طبيعية";
}

function formatDuration(minutes) {
  const value = Number(minutes);
  if (!Number.isFinite(value) || value <= 0) return null;
  if (value < 60) return `${Math.round(value)} دقيقة`;
  const hours = Math.round((value / 60) * 10) / 10;
  return `${hours.toLocaleString("ar")} ساعة`;
}

function courseRecord({
  externalKey,
  slugPrefix,
  title,
  organization,
  country,
  website,
  category,
  duration,
  format,
  sourceName,
  sourceUrl,
  license,
  attributes,
}) {
  const details = unique([duration, format]).join(" · ");
  return catalogRecord({
    type: "COURSE",
    externalKey,
    slugPrefix,
    title,
    summary: `مورد تعليمي رسمي بعنوان «${limit(title, 220)}» تقدمه ${organization}.${details ? ` بياناته المنشورة تشير إلى: ${details}.` : ""}`,
    content: `بيانات هذا السجل مأخوذة من الكتالوج الرسمي للجهة وتشمل العنوان والرابط والتصنيف فقط. افتح الصفحة الأصلية للتأكد من المحتوى الحالي، لغة التدريس، المتطلبات، مدة الدراسة، خيارات الشهادة وأي رسوم قبل أن تبدأ.`,
    category,
    organization,
    country,
    website,
    meta: unique([duration, format, sourceName]).join(" · "),
    sourceName,
    sourceUrl,
    license,
    attributes,
  });
}

async function loadMitCourses() {
  const first = await fetchJson(SOURCES.mitApi);
  const offsets = [];
  for (let offset = 100; offset < Number(first.count ?? 0); offset += 100) offsets.push(offset);
  const pages = await mapLimit(offsets, 4, async (offset) => {
    const url = new URL(SOURCES.mitApi);
    url.searchParams.set("offset", String(offset));
    const page = await fetchJson(url);
    return array(page.results);
  });
  const rows = [...array(first.results), ...pages.flat()];
  const records = rows
    .filter((item) => item?.published !== false && item?.title)
    .map((item) => {
      const run = array(item.runs).find((candidate) => candidate?.id === item.best_run_id)
        ?? array(item.runs).find((candidate) => candidate?.published !== false)
        ?? {};
      const website = httpsUrl(item.url) ?? httpsUrl(run.url) ?? httpsUrl(item.learn_url);
      if (!website) return null;
      const levels = unique(array(run.level).map((entry) => entry?.name));
      const format = unique([
        item.free ? "مجاني" : null,
        ...array(item.pace).map((entry) => entry?.name),
        ...levels,
      ]).join(" · ");
      return courseRecord({
        externalKey: `mit-ocw:${item.id}`,
        slugPrefix: "mit-ocw",
        title: item.title,
        organization: "Massachusetts Institute of Technology (MIT)",
        country: "الولايات المتحدة",
        website,
        category: courseCategory(item),
        duration: item.duration || run.duration || null,
        format,
        sourceName: "MIT OpenCourseWare",
        sourceUrl: SOURCES.mitOcw,
        license: item.license_cc ? "Creative Commons — راجع ترخيص صفحة المساق" : null,
        attributes: {
          provider: "MIT OpenCourseWare",
          free: Boolean(item.free),
          levels,
          topics: unique([
            ...array(item.topics).map((topic) => topic?.name),
            ...array(item.ocw_topics),
          ]).slice(0, 12),
          lastModified: isoDate(item.last_modified),
          resourceType: "course",
        },
      });
    })
    .filter(Boolean);
  if (records.length < 2_400) {
    throw new Error(`MIT Learn returned only ${records.length} usable OCW courses`);
  }
  return records;
}

function microsoftDuration(item, kind) {
  if (Number(item.duration_in_minutes) > 0) return Number(item.duration_in_minutes);
  if (Number(item.duration_in_hours) > 0) return Number(item.duration_in_hours) * 60;
  if (kind === "course" && Number(item.duration) > 0) return Number(item.duration) * 60;
  return null;
}

async function loadMicrosoftCourses() {
  const catalog = await fetchJson(SOURCES.microsoftCatalog);
  const groups = [
    ["module", array(catalog.modules)],
    ["learning-path", array(catalog.learningPaths)],
    ["course", array(catalog.courses)],
  ];
  const labels = {
    module: "وحدة تعليمية",
    "learning-path": "مسار تعلم",
    course: "دورة تدريبية",
  };
  const records = groups.flatMap(([kind, items]) =>
    items.map((item) => {
      const website = httpsUrl(item.url);
      if (!item?.title || !item?.uid || !website) return null;
      const minutes = microsoftDuration(item, kind);
      const duration = formatDuration(minutes);
      const levels = unique(array(item.levels));
      const format = unique([labels[kind], ...levels]).join(" · ");
      return courseRecord({
        externalKey: `microsoft-learn:${item.uid}`,
        slugPrefix: `microsoft-${kind}`,
        title: item.title,
        organization: "Microsoft Learn",
        country: "عالمي",
        website,
        category: courseCategory(item),
        duration,
        format,
        sourceName: "Microsoft Learn",
        sourceUrl: SOURCES.microsoftLearn,
        license: "Metadata supplied by Microsoft Learn Catalog API",
        attributes: {
          provider: "Microsoft Learn",
          resourceType: kind,
          durationMinutes: minutes,
          levels,
          roles: unique(array(item.roles)).slice(0, 12),
          subjects: unique(array(item.subjects)).slice(0, 12),
          products: unique(array(item.products)).slice(0, 12),
          locale: item.locale ?? "en-us",
          lastModified: isoDate(item.last_modified),
        },
      });
    }).filter(Boolean),
  );
  if (records.length < 4_200) {
    throw new Error(`Microsoft Learn returned only ${records.length} usable learning resources`);
  }
  return records;
}

function jobRecord({
  sourceKey,
  sourceName,
  sourceUrl,
  title,
  company,
  location,
  website,
  remote,
  types,
  tags,
  publishedAt,
  salary,
}) {
  const cleanLocation = limit(location || (remote ? "عن بُعد" : "حسب الإعلان"), 100);
  const cleanCompany = limit(company || "جهة معلنة", 180);
  const cleanTitle = limit(title, 300);
  const published = isoDate(publishedAt);
  return catalogRecord({
    type: "JOB",
    externalKey: `${sourceName}:${sourceKey}`,
    slugPrefix: `job-${asciiSlug(sourceName).slice(0, 24) || "source"}`,
    title: cleanTitle,
    summary: `فرصة عمل بعنوان «${limit(cleanTitle, 210)}» لدى ${cleanCompany} في ${cleanLocation}، منشورة عبر ${sourceName}.`,
    content: `يعرض جامعتي بيانات وصفية مختصرة لهذه الوظيفة ولا ينسخ وصفها الكامل. افتح إعلان ${sourceName} للتحقق من بقاء الشاغر، المهام، شروط الخبرة، الأهلية، الراتب وطريقة التقديم قبل إرسال أي بيانات شخصية.`,
    category: jobCategory(cleanTitle, tags),
    organization: cleanCompany,
    country: cleanLocation,
    website,
    meta: unique([remote ? "عن بُعد" : null, ...array(types), published]).join(" · "),
    sourceName,
    sourceUrl,
    attributes: {
      recordKind: "job-listing",
      publicSourceLink: true,
      attributionRequired: true,
      remote: Boolean(remote),
      jobTypes: unique(array(types)).slice(0, 8),
      tags: unique(array(tags)).slice(0, 16),
      publishedAt: published,
      salary: salary ? limit(salary, 120) : null,
    },
  });
}

function freelancePortalRecords() {
  return FREELANCE_PORTALS.map((portal) => catalogRecord({
    type: "JOB",
    externalKey: `freelance-portal:${portal.key}`,
    slugPrefix: "freelance-portal",
    title: portal.title,
    summary: `رابط مباشر إلى ${portal.organization} للبحث عن مشاريع أو خدمات عمل حر. أُضيفت البوابة نفسها فقط دون نسخ إعلانات أو ملفات مستخدمين.`,
    content: `هذه بطاقة دليل للمنصة الرسمية وليست إعلان وظيفة منسوخًا. استخدم الرابط لقراءة الشروط، إنشاء حساب والبحث داخل ${portal.organization} مباشرة، وتحقق من الرسوم وآلية الدفع وحماية التعامل قبل قبول أي مشروع.`,
    category: "منصة عمل حر",
    organization: portal.organization,
    country: portal.country,
    website: portal.url,
    meta: "بوابة رسمية · بحث خارجي",
    sourceName: portal.organization,
    sourceUrl: portal.url,
    attributes: {
      recordKind: "freelance-portal",
      publicSourceLink: true,
      listingsCopied: false,
    },
  }));
}

async function loadArbeitnowJobs() {
  const rows = [];
  const seen = new Set();
  for (let page = 1; page <= LIMITS.arbeitnowPages; page += 1) {
    const url = new URL(SOURCES.arbeitnowApi);
    url.searchParams.set("page", String(page));
    let payload;
    try {
      payload = await fetchJson(url);
    } catch (error) {
      if (rows.length > 0) {
        process.stderr.write(`Arbeitnow stopped at page ${page}: ${error.message}\n`);
        break;
      }
      throw error;
    }
    const pageRows = array(payload.data);
    let fresh = 0;
    for (const row of pageRows) {
      const key = String(row.slug ?? row.url ?? "");
      if (!key || seen.has(key)) continue;
      seen.add(key);
      rows.push(row);
      fresh += 1;
    }
    if (pageRows.length === 0 || fresh === 0 || !payload.links?.next) break;
    await delay(300);
  }
  return rows.map((row) => jobRecord({
    sourceKey: row.slug,
    sourceName: "Arbeitnow",
    sourceUrl: SOURCES.arbeitnowDocs,
    title: row.title,
    company: row.company_name,
    location: row.location,
    website: row.url,
    remote: row.remote,
    types: row.job_types,
    tags: row.tags,
    publishedAt: row.created_at,
  }));
}

async function loadJobicyJobs() {
  const payload = await fetchJson(SOURCES.jobicyApi);
  return array(payload.jobs).map((row) => jobRecord({
    sourceKey: row.id ?? row.jobSlug,
    sourceName: "Jobicy",
    sourceUrl: SOURCES.jobicyDocs,
    title: row.jobTitle,
    company: row.companyName,
    location: row.jobGeo,
    website: row.url,
    remote: true,
    types: row.jobType,
    tags: [...array(row.jobIndustry), row.jobLevel].filter(Boolean),
    publishedAt: row.pubDate,
    salary: unique([row.annualSalaryMin, row.annualSalaryMax, row.salaryCurrency]).join(" "),
  }));
}

async function loadRemoteOkJobs() {
  const payload = await fetchJson(SOURCES.remoteOkApi);
  return array(payload)
    .filter((row) => row?.id && row?.position && httpsUrl(row.url ?? row.apply_url))
    .map((row) => jobRecord({
      sourceKey: row.id,
      sourceName: "Remote OK",
      sourceUrl: SOURCES.remoteOkApi,
      title: row.position,
      company: row.company,
      location: row.location || "عن بُعد",
      website: row.url ?? row.apply_url,
      remote: true,
      types: [],
      tags: row.tags,
      publishedAt: row.date ?? row.epoch,
      salary: Number(row.salary_min) > 0 || Number(row.salary_max) > 0
        ? `${row.salary_min || ""}–${row.salary_max || ""}`
        : null,
    }));
}

async function loadRemotiveJobs() {
  const payload = await fetchJson(SOURCES.remotiveApi);
  return array(payload.jobs).map((row) => jobRecord({
    sourceKey: row.id,
    sourceName: "Remotive",
    sourceUrl: SOURCES.remotiveDocs,
    title: row.title,
    company: row.company_name,
    location: row.candidate_required_location || "عن بُعد",
    website: row.url,
    remote: true,
    types: [row.job_type].filter(Boolean),
    tags: [row.category, ...array(row.tags)].filter(Boolean),
    publishedAt: row.publication_date,
    salary: row.salary,
  }));
}

async function loadJobs() {
  const [arbeitnow, jobicy, remoteOk, remotive] = await Promise.all([
    loadArbeitnowJobs(),
    loadJobicyJobs(),
    loadRemoteOkJobs(),
    loadRemotiveJobs(),
  ]);
  const rows = [
    ...freelancePortalRecords(),
    ...arbeitnow,
    ...jobicy,
    ...remoteOk,
    ...remotive,
  ];
  const seenListings = new Set();
  return rows.filter((record) => {
    if (record.attributes.recordKind !== "job-listing") return true;
    const signature = `${inlineText(record.title).toLowerCase()}\0${inlineText(record.organization).toLowerCase()}`;
    if (seenListings.has(signature)) return false;
    seenListings.add(signature);
    return true;
  });
}

async function loadOpenAlexPages(endpoint, { filter = null, sort = null, select, limit: rowLimit = Infinity } = {}) {
  const rows = [];
  let cursor = "*";
  while (rows.length < rowLimit && cursor) {
    const url = new URL(endpoint);
    url.searchParams.set("per-page", String(Math.min(200, rowLimit - rows.length)));
    url.searchParams.set("cursor", cursor);
    url.searchParams.set("mailto", "contact@jamieati.org");
    if (filter) url.searchParams.set("filter", filter);
    if (sort) url.searchParams.set("sort", sort);
    if (select) url.searchParams.set("select", select);
    const payload = await fetchJson(url);
    const pageRows = array(payload.results);
    rows.push(...pageRows);
    const next = payload.meta?.next_cursor;
    if (pageRows.length === 0 || !next || next === cursor) break;
    cursor = next;
  }
  return rows.slice(0, rowLimit);
}

async function loadArticles() {
  const works = await loadOpenAlexPages(SOURCES.openAlexWorks, {
    filter: `type:article,is_oa:true,from_publication_date:2025-01-01,to_publication_date:${TODAY},language:en`,
    sort: "publication_date:desc",
    select: "id,doi,title,publication_date,primary_location,primary_topic,open_access,language",
    limit: LIMITS.articles,
  });
  const records = works.map((work) => {
    const topic = work.primary_topic ?? {};
    const source = work.primary_location?.source?.display_name || "منشور بحثي";
    const website = httpsUrl(work.doi)
      ?? httpsUrl(work.primary_location?.landing_page_url)
      ?? httpsUrl(work.id);
    if (!work?.id || !work?.title || !website) return null;
    const category = articleCategory(topic);
    const date = isoDate(work.publication_date);
    return catalogRecord({
      type: "ARTICLE",
      externalKey: `openalex-work:${work.id.split("/").at(-1)}`,
      slugPrefix: "open-research",
      title: work.title,
      summary: `مقال بحثي مفتوح الوصول بعنوان «${limit(work.title, 235)}»${date ? ` منشور بتاريخ ${date}` : ""} ضمن مجال ${category}.`,
      content: `يحتوي هذا السجل على بيانات وصفية فقط من OpenAlex ولا ينسخ الملخص أو نص المقال. استخدم الرابط الأصلي لقراءة النسخة المتاحة، والتحقق من المؤلفين والمنهج والنتائج والترخيص قبل الاستشهاد العلمي.`,
      category,
      organization: source,
      country: "عالمي",
      website,
      meta: unique([date, "وصول مفتوح", category]).join(" · "),
      sourceName: "OpenAlex",
      sourceUrl: SOURCES.openAlexDocs,
      officialUrl: work.id,
      license: "OpenAlex metadata: CC0",
      attributes: {
        recordKind: "research-article-metadata",
        openAlexId: work.id,
        doi: httpsUrl(work.doi),
        publicationDate: date,
        openAccessStatus: work.open_access?.oa_status ?? null,
        topic: topic.display_name ?? null,
        field: topic.field?.display_name ?? null,
        domain: topic.domain?.display_name ?? null,
        language: work.language ?? "en",
      },
    });
  }).filter(Boolean);
  if (records.length < Math.min(2_000, LIMITS.articles)) {
    throw new Error(`OpenAlex returned only ${records.length} usable articles`);
  }
  return records;
}

async function loadSpecialities() {
  const topics = await loadOpenAlexPages(SOURCES.openAlexTopics, {
    select: "id,display_name,subfield,field,domain,keywords,works_count",
  });
  const records = topics.map((topic) => {
    const website = httpsUrl(topic.id);
    if (!topic?.id || !topic?.display_name || !website) return null;
    const domain = topic.domain?.display_name || "مجالات متعددة";
    const field = topic.field?.display_name || domain;
    const subfield = topic.subfield?.display_name || field;
    const category = OPENALEX_DOMAIN_ARABIC.get(domain) ?? limit(domain, 100);
    return catalogRecord({
      type: "SPECIALITY",
      externalKey: `openalex-topic:${topic.id.split("/").at(-1)}`,
      slugPrefix: "speciality",
      title: `تخصص ${topic.display_name}`,
      summary: `مسار معرفي مصنّف في OpenAlex باسم «${limit(topic.display_name, 220)}»، ويندرج ضمن ${subfield} ثم ${field}.`,
      content: `هذا الدليل مبني على تصنيف OpenAlex المفتوح للموضوعات البحثية. استخدمه لفهم موقع التخصص بين المجالات، ثم قارن خطط الجامعات الفعلية لأن اسم البرنامج ومتطلباته ومساراته المهنية قد تختلف من مؤسسة إلى أخرى.`,
      category,
      organization: "OpenAlex / OurResearch",
      country: "عالمي",
      website,
      meta: unique([category, field, subfield]).join(" · "),
      sourceName: "OpenAlex Topics",
      sourceUrl: "https://help.openalex.org/data/topics/",
      license: "OpenAlex metadata: CC0",
      attributes: {
        openAlexId: topic.id,
        topic: topic.display_name,
        subfield,
        field,
        domain,
        keywords: unique(array(topic.keywords).map((keyword) => keyword?.display_name ?? keyword)).slice(0, 10),
        worksCount: Number(topic.works_count) || 0,
      },
    });
  }).filter(Boolean);
  if (records.length < 4_000) {
    throw new Error(`OpenAlex returned only ${records.length} usable topics`);
  }
  return records;
}

function parseCsvRecord(record) {
  const fields = [];
  let value = "";
  let quoted = false;
  for (let index = 0; index < record.length; index += 1) {
    const character = record[index];
    if (character === '"') {
      if (quoted && record[index + 1] === '"') {
        value += '"';
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (character === "," && !quoted) {
      fields.push(value);
      value = "";
    } else {
      value += character;
    }
  }
  fields.push(value);
  return { fields, complete: !quoted };
}

function semicolonList(value) {
  return unique(String(value ?? "").split(";").map((entry) => entry.trim()));
}

function arabicCountry(code, fallback) {
  const normalized = String(code ?? "").toUpperCase();
  try {
    return normalized.length === 2 ? ARABIC_REGIONS.of(normalized) || fallback : fallback;
  } catch {
    return fallback;
  }
}

function balancedSample(records, limitCount) {
  const buckets = new Map();
  for (const record of records) {
    const bucket = record.countryCode || "ZZ";
    if (!buckets.has(bucket)) buckets.set(bucket, []);
    buckets.get(bucket).push(record);
  }
  const orderedBuckets = [...buckets.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([, items]) => items.toSorted((left, right) =>
      sha(left.id).localeCompare(sha(right.id))));
  const result = [];
  let depth = 0;
  while (result.length < limitCount) {
    let added = false;
    for (const bucket of orderedBuckets) {
      if (bucket[depth]) {
        result.push(bucket[depth]);
        added = true;
        if (result.length === limitCount) break;
      }
    }
    if (!added) break;
    depth += 1;
  }
  return result;
}

async function downloadFile(url, path) {
  const response = await fetchWithRetry(url);
  if (!response.body) throw new Error(`No response body while downloading ${url}`);
  await pipeline(Readable.fromWeb(response.body), createWriteStream(path));
}

async function extractRorCandidates(zipPath, csvName) {
  const process = spawn("unzip", ["-p", zipPath, csvName], {
    stdio: ["ignore", "pipe", "pipe"],
  });
  let stderr = "";
  process.stderr.setEncoding("utf8");
  process.stderr.on("data", (chunk) => { stderr += chunk; });
  const lines = createInterface({ input: process.stdout, crlfDelay: Infinity });
  let headers = null;
  let buffered = "";
  const universities = [];
  const organizations = [];
  for await (const line of lines) {
    buffered = buffered ? `${buffered}\n${line}` : line;
    const parsed = parseCsvRecord(buffered);
    if (!parsed.complete) continue;
    buffered = "";
    if (!headers) {
      headers = parsed.fields;
      continue;
    }
    const row = Object.fromEntries(headers.map((header, index) => [header, parsed.fields[index] ?? ""]));
    if (row.status !== "active") continue;
    const types = semicolonList(row.types);
    const displayName = inlineText(row["names.types.ror_display"]);
    const id = httpsUrl(row.id);
    if (!displayName || !id || types.length === 0) continue;
    const candidate = {
      id,
      name: displayName,
      types,
      website: semicolonList(row["links.type.website"]).map((link) => httpsUrl(link)).find(Boolean) ?? id,
      countryCode: row["locations.geonames_details.country_code"],
      country: arabicCountry(
        row["locations.geonames_details.country_code"],
        inlineText(row["locations.geonames_details.country_name"]) || "عالمي",
      ),
      city: inlineText(row["locations.geonames_details.name"]),
      domains: semicolonList(row.domains),
      established: Number.parseInt(row.established, 10) || null,
    };
    if (types.includes("education")) universities.push(candidate);
    else organizations.push(candidate);
  }
  const exitCode = await new Promise((resolveExit) => process.once("close", resolveExit));
  if (exitCode !== 0) throw new Error(`unzip exited with ${exitCode}: ${stderr.trim()}`);
  return { universities, organizations };
}

function organizationCategory(types) {
  if (types.includes("government")) return "جهة حكومية";
  if (types.includes("company")) return "شركة";
  if (types.includes("healthcare")) return "مؤسسة صحية";
  if (types.includes("archive")) return "مؤسسة معرفية";
  if (types.includes("funder") || types.includes("nonprofit")) return "مؤسسة غير ربحية";
  if (types.includes("facility")) return "مؤسسة بحثية";
  return "مؤسسة دولية";
}

function rorRecord(candidate, type, dumpVersion) {
  const isUniversity = type === "UNIVERSITY";
  const location = unique([candidate.city, candidate.country]).join("، ");
  const category = isUniversity ? "جامعة أو مؤسسة تعليم عالٍ" : organizationCategory(candidate.types);
  return catalogRecord({
    type,
    externalKey: `ror:${candidate.id.split("/").at(-1)}`,
    slugPrefix: isUniversity ? "university" : "organization",
    title: candidate.name,
    summary: `${isUniversity ? "مؤسسة تعليمية" : "مؤسسة"} نشطة مسجلة في Research Organization Registry باسم «${limit(candidate.name, 230)}»${location ? ` وموقعها ${location}` : ""}.`,
    content: `هذا السجل دليل تعريفي مبني على بيانات ROR المفتوحة، وليس تقييمًا أو اعتمادًا أكاديميًا. راجع موقع الجهة وسجل ROR، ثم تحقق من الاعتماد والبرامج وشروط القبول أو طبيعة النشاط من الجهات المختصة قبل اتخاذ قرار.`,
    category,
    organization: candidate.name,
    country: candidate.country,
    website: candidate.website,
    meta: unique([
      candidate.city,
      candidate.established ? `تأسست ${candidate.established}` : null,
      category,
    ]).join(" · "),
    sourceName: "Research Organization Registry (ROR)",
    sourceUrl: candidate.id,
    officialUrl: candidate.id,
    license: "ROR metadata: CC0 1.0",
    attributes: {
      rorId: candidate.id,
      rorTypes: candidate.types,
      countryCode: candidate.countryCode || null,
      city: candidate.city || null,
      domains: candidate.domains,
      established: candidate.established,
      dumpVersion,
    },
  });
}

async function loadRorRecords() {
  const metadata = await fetchJson(SOURCES.rorRecord);
  const file = array(metadata.files).find((candidate) => candidate.key?.endsWith("-ror-data.zip"));
  if (!file?.links?.self || !file?.key) throw new Error("Could not locate the latest ROR data dump");
  const temporaryDirectory = await mkdtemp(join(tmpdir(), "jameati-ror-"));
  const zipPath = join(temporaryDirectory, file.key);
  try {
    const override = process.env.JAMEATI_ROR_ZIP_PATH;
    const archive = override ? resolve(override) : zipPath;
    if (!override) await downloadFile(file.links.self, archive);
    const csvName = file.key.replace(/\.zip$/u, ".csv");
    const candidates = await extractRorCandidates(archive, csvName);
    // Jordanian universities are maintained in a curated Ministry-backed feed so their
    // Arabic names, campus locations, and official websites are never replaced by ROR fallbacks.
    const universities = balancedSample(
      candidates.universities.filter((candidate) => candidate.countryCode !== "JO"),
      LIMITS.universities,
    )
      .map((candidate) => rorRecord(candidate, "UNIVERSITY", metadata.metadata?.version ?? file.key));
    const organizations = balancedSample(candidates.organizations, LIMITS.organizations)
      .map((candidate) => rorRecord(candidate, "ORGANIZATION", metadata.metadata?.version ?? file.key));
    if (universities.length < LIMITS.universities || organizations.length < LIMITS.organizations) {
      throw new Error(
        `ROR yielded ${universities.length} universities and ${organizations.length} organizations`,
      );
    }
    return { universities, organizations, sourceVersion: metadata.metadata?.version ?? file.key };
  } finally {
    await rm(temporaryDirectory, { recursive: true, force: true });
  }
}

function validateRecord(record) {
  const requiredStrings = [
    "type",
    "sourceExternalKey",
    "sourceFingerprint",
    "slug",
    "title",
    "summary",
    "content",
    "category",
    "website",
    "sourceName",
    "sourceUrl",
    "officialUrl",
    "verifiedAt",
  ];
  for (const field of requiredStrings) {
    if (typeof record[field] !== "string" || !record[field].trim()) {
      throw new Error(`${field} is missing for ${record.sourceExternalKey}`);
    }
  }
  const lengths = {
    sourceExternalKey: 190,
    slug: 190,
    title: 300,
    summary: 500,
    category: 100,
    organization: 180,
    country: 100,
    image: 500,
    website: 500,
    meta: 80,
    sourceName: 180,
    sourceUrl: 500,
    officialUrl: 500,
  };
  for (const [field, maxLength] of Object.entries(lengths)) {
    if (record[field] != null && String(record[field]).length > maxLength) {
      throw new Error(`${field} is too long for ${record.sourceExternalKey}`);
    }
  }
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/u.test(record.slug)) {
    throw new Error(`Invalid slug ${record.slug}`);
  }
  for (const field of ["website", "sourceUrl", "officialUrl"]) {
    if (new URL(record[field]).protocol !== "https:") {
      throw new Error(`${field} is not HTTPS for ${record.sourceExternalKey}`);
    }
  }
  if (!["ARTICLE", "COURSE", "JOB", "ORGANIZATION", "SPECIALITY", "UNIVERSITY"].includes(record.type)) {
    throw new Error(`Unknown catalog type ${record.type}`);
  }
  if (!record.attributes || Array.isArray(record.attributes) || typeof record.attributes !== "object") {
    throw new Error(`attributes is invalid for ${record.sourceExternalKey}`);
  }
}

function deduplicate(records, existing) {
  const slugs = new Set(existing.map((record) => `${record.type}:${record.slug.toLowerCase()}`));
  const urls = new Set(existing.map((record) => `${record.type}:${record.officialUrl?.toLowerCase()}`));
  const externalKeys = new Set();
  const accepted = [];
  const skipped = [];
  for (const record of records) {
    validateRecord(record);
    const slug = `${record.type}:${record.slug.toLowerCase()}`;
    const url = `${record.type}:${record.officialUrl.toLowerCase()}`;
    const externalKey = `${record.type}:${record.sourceExternalKey.toLowerCase()}`;
    if (slugs.has(slug) || urls.has(url) || externalKeys.has(externalKey)) {
      skipped.push(record.sourceExternalKey);
      continue;
    }
    slugs.add(slug);
    urls.add(url);
    externalKeys.add(externalKey);
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
  const core = JSON.parse(await readFile(CORE_CATALOG, "utf8"));
  const [mitCourses, microsoftCourses, jobs, articles, specialities] = await Promise.all([
    loadMitCourses(),
    loadMicrosoftCourses(),
    loadJobs(),
    loadArticles(),
    loadSpecialities(),
  ]);
  process.stdout.write(`MIT OpenCourseWare: ${mitCourses.length}\n`);
  process.stdout.write(`Microsoft Learn: ${microsoftCourses.length}\n`);
  process.stdout.write(`Jobs and freelance portals: ${jobs.length}\n`);
  process.stdout.write(`OpenAlex articles: ${articles.length}\n`);
  process.stdout.write(`OpenAlex specialities: ${specialities.length}\n`);

  const ror = await loadRorRecords();
  process.stdout.write(`ROR universities: ${ror.universities.length}\n`);
  process.stdout.write(`ROR organizations: ${ror.organizations.length}\n`);

  const generated = [
    ...mitCourses,
    ...microsoftCourses,
    ...jobs,
    ...articles,
    ...specialities,
    ...ror.universities,
    ...ror.organizations,
  ];
  const { accepted, skipped } = deduplicate(generated, array(core.records));
  const counts = countsBy(accepted, (record) => record.type);
  const minimumCounts = {
    ARTICLE: Math.min(2_000, LIMITS.articles),
    COURSE: 6_500,
    JOB: 250,
    ORGANIZATION: LIMITS.organizations,
    SPECIALITY: 4_000,
    UNIVERSITY: LIMITS.universities,
  };
  for (const [type, minimum] of Object.entries(minimumCounts)) {
    if ((counts[type] ?? 0) < minimum) {
      throw new Error(`${type} generated ${counts[type] ?? 0}; expected at least ${minimum}`);
    }
  }

  const feed = {
    feedId: "official-expanded-catalog",
    version: `official-expanded-catalog-${TODAY}.1`,
    verifiedAt: new Date().toISOString(),
    verifiedBy: "فريق تحرير جامعتي — مزامنة بيانات وصفية من المصادر الرسمية والمفتوحة",
    completeSnapshot: true,
    minimumCounts,
    counts,
    sourceVersions: {
      ror: ror.sourceVersion,
      microsoftLearn: "live-catalog",
      mitLearn: "live-api",
      openAlex: "live-api",
      jobs: "live-public-feeds",
    },
    records: accepted,
  };
  await writeFile(OUTPUT, `${JSON.stringify(feed)}\n`, "utf8");
  process.stdout.write(
    `Wrote ${accepted.length} records to ${OUTPUT}\n` +
      `Skipped duplicates: ${skipped.length}\n` +
      `Types: ${JSON.stringify(counts)}\n` +
      `Sources: ${JSON.stringify(countsBy(accepted, (record) => record.sourceName))}\n`,
  );
}

await main();
