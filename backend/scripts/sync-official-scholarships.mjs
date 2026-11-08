import { createHash } from "node:crypto";
import { writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const EACEA_CATALOGUE =
  "https://www.eacea.ec.europa.eu/scholarships/erasmus-mundus-catalogue_en";
const EDUCATION_USA_CATALOGUE = "https://educationusa.state.gov/find-financial-aid";
const OUTPUT = resolve(
  dirname(fileURLToPath(import.meta.url)),
  "../src/main/resources/data/official-scholarships.json",
);
const USER_AGENT =
  "JameatiOfficialCatalogueImporter/1.0 (official metadata sync; https://jamieati.org)";
const SNAPSHOT_DATE = new Date().toISOString().slice(0, 10);

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

const MONTHS = new Map(
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

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

async function fetchHtml(url) {
  let lastError;
  for (let attempt = 1; attempt <= 4; attempt += 1) {
    try {
      const response = await fetch(url, {
        headers: {
          accept: "text/html,application/xhtml+xml",
          "accept-language": "en-US,en;q=0.9",
          "user-agent": USER_AGENT,
        },
        redirect: "follow",
        signal: AbortSignal.timeout(45_000),
      });
      if (response.ok) return await response.text();
      if (response.status !== 429 && response.status < 500) {
        throw new Error(`HTTP ${response.status} while reading ${url}`);
      }
      lastError = new Error(`HTTP ${response.status} while reading ${url}`);
    } catch (error) {
      lastError = error;
    }
    if (attempt < 4) await delay(attempt * 750);
  }
  throw new Error(`Failed to read ${url}: ${lastError?.message ?? "unknown error"}`, {
    cause: lastError,
  });
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
    value
      .replace(/<br\s*\/?>/giu, "\n")
      .replace(/<\/(?:div|li|p|tr)>/giu, "\n")
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

function limit(value, maxLength) {
  const normalized = value.trim();
  if (normalized.length <= maxLength) return normalized;
  return `${normalized.slice(0, Math.max(1, maxLength - 1)).trimEnd()}…`;
}

function attribute(attributes, name) {
  const match = attributes.match(
    new RegExp(`${name}\\s*=\\s*(?:"([^"]*)"|'([^']*)')`, "iu"),
  );
  return decodeHtml(match?.[1] ?? match?.[2] ?? "");
}

function divContent(html, className) {
  const expression = new RegExp(
    `<div\\b[^>]*class=(?:"[^"]*\\b${className}\\b[^"]*"|'[^']*\\b${className}\\b[^']*')[^>]*>([\\s\\S]*?)<\\/div>`,
    "iu",
  );
  return expression.exec(html)?.[1] ?? "";
}

function sha(value) {
  return createHash("sha256").update(value).digest("hex");
}

function asciiSlug(value) {
  return value
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
  const body = (asciiSlug(label) || "opportunity").slice(0, available).replace(/-+$/u, "");
  return `${prefix}-${body}-${suffix}`;
}

function absoluteHttps(value, baseUrl) {
  if (!value) return null;
  try {
    const url = new URL(value, baseUrl);
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

function parseLatestExplicitDate(value) {
  const matches = [];
  const pattern =
    /\b(January|February|March|April|May|June|July|August|September|October|November|December)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,)?\s+(20\d{2})\b/giu;
  for (const match of value.matchAll(pattern)) {
    const month = MONTHS.get(match[1].toLowerCase());
    const day = Number.parseInt(match[2], 10);
    const year = Number.parseInt(match[3], 10);
    const date = new Date(Date.UTC(year, month - 1, day));
    if (
      date.getUTCFullYear() === year &&
      date.getUTCMonth() === month - 1 &&
      date.getUTCDate() === day
    ) {
      matches.push(
        `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`,
      );
    }
  }
  return matches.sort().at(-1) ?? null;
}

function isAlwaysOpen(deadline, deadlineType) {
  const value = `${deadline} ${deadlineType}`;
  return /\b(?:no deadline|rolling admissions?|applications? (?:are )?accepted (?:on a )?rolling basis|year[- ]round|throughout the year)\b/iu.test(
    value,
  );
}

function fundingType(title) {
  const value = title;
  const negated =
    /(?:full scholarships? (?:are|is) not|(?:does|do) not offer (?:a )?full|no full scholarships?|not (?:a )?fully[- ]funded)/iu.test(
      value,
    );
  const fullyFunded = /\b(?:fully[- ]funded|full[- ]ride)\b/iu.test(value);
  return fullyFunded && !negated ? "FULLY_FUNDED" : "PARTIALLY_FUNDED";
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

function parseEaceaCards(html) {
  const records = [];
  const cards = html.matchAll(
    /<article\b[^>]*class=(?:"[^"]*\becl-card\b[^"]*"|'[^']*\becl-card\b[^']*')[^>]*>([\s\S]*?)<\/article>/giu,
  );
  for (const cardMatch of cards) {
    const card = cardMatch[1];
    const titleBlock = divContent(card, "ecl-content-block__title");
    const titleLink = titleBlock.match(/<a\b([^>]*)>([\s\S]*?)<\/a>/iu);
    const title = inlineText(
      /<span\b[^>]*class=(?:"[^"]*\becl-link__label\b[^"]*"|'[^']*\becl-link__label\b[^']*')[^>]*>([\s\S]*?)<\/span>/iu.exec(
        titleLink?.[2] ?? titleBlock,
      )?.[1] ?? titleLink?.[2] ?? titleBlock,
    );
    if (!title) throw new Error("An EACEA catalogue card has no title");
    const consortiumHref = attribute(titleLink?.[1] ?? "", "href");
    const modernProject = card.match(
      /https:\/\/erasmus-plus\.ec\.europa\.eu\/projects\/search\/details\/([a-z0-9-]+)/iu,
    );
    const legacyProject = card.match(
      /https:\/\/ec\.europa\.eu\/programmes\/erasmus-plus\/projects\/eplus-project-details\/#project\/([a-z0-9-]+)/iu,
    );
    const projectMatch = modernProject ?? legacyProject;
    const fallbackSource = new URL(EACEA_CATALOGUE);
    fallbackSource.searchParams.set("oe_project_title", title);
    const projectId =
      projectMatch?.[1] ?? `catalogue-${sha(`${title}\0${consortiumHref}`).slice(0, 16)}`;
    const projectUrl = projectMatch?.[0] ?? fallbackSource.toString();
    const consortiumUrl = absoluteHttps(consortiumHref, EACEA_CATALOGUE);
    const description = inlineText(divContent(card, "ecl-content-block__description"));
    const acronym =
      description.split(/\s+[-–—]\s+Project overview/iu)[0]?.trim() || "EMJM";
    records.push({ acronym, consortiumUrl, projectId, projectUrl, title });
  }
  return records;
}

async function loadEaceaRecords() {
  const firstHtml = await fetchHtml(`${EACEA_CATALOGUE}?page=0`);
  const totalMatch = firstHtml.match(/Projects[\s\S]{0,100}?\((\d+)\)/iu);
  if (!totalMatch) throw new Error("Could not discover the EACEA catalogue total");
  const expectedTotal = Number.parseInt(totalMatch[1], 10);
  const pageCount = Math.ceil(expectedTotal / 20);
  const remainingPages = await mapLimit(
    Array.from({ length: pageCount - 1 }, (_, index) => index + 1),
    4,
    async (page) => fetchHtml(`${EACEA_CATALOGUE}?page=${page}`),
  );
  const sourceRows = [firstHtml, ...remainingPages].flatMap(parseEaceaCards);
  const uniqueRows = [...new Map(sourceRows.map((row) => [row.projectId, row])).values()];
  if (uniqueRows.length !== expectedTotal) {
    throw new Error(
      `Expected ${expectedTotal} EACEA records but parsed ${sourceRows.length} cards and ${uniqueRows.length} unique project IDs`,
    );
  }

  return uniqueRows.map((row, index) => ({
    externalKey: `eacea-emjm-${row.projectId}`,
    slug: stableSlug("erasmus-mundus", `${row.acronym}-${row.title}`, row.projectId),
    title: limit(`منحة Erasmus Mundus: ${row.title}`, 300),
    shortDescription:
      "برنامج ماجستير مشترك دولي مدرج في كتالوج Erasmus Mundus الرسمي. قد تتوفر منحة للطلبة المتفوقين؛ تحقّق من دورة التمويل والموعد في موقع البرنامج.",
    description: `نبذة\n${row.title} برنامج ماجستير مشترك مدرج في كتالوج Erasmus Mundus الرسمي وتقدمه جامعات من عدة دول.\n\nالتمويل\nيتيح Erasmus Mundus منحًا تنافسية قد تغطي تكاليف المشاركة والسفر والتأشيرة والمعيشة. تنبّه EACEA إلى أن توفر المنحة يختلف بين البرامج ودورات التمويل، لذلك يجب تأكيده في موقع البرنامج.\n\nالتقديم\nتُرسل الطلبات مباشرة إلى اتحاد البرنامج. أغلب البرامج تستقبل الطلبات بين أكتوبر ويناير، لكن الموعد والشروط يختلفان؛ راجع رابط البرنامج والمشروع الرسمي قبل التقديم.`,
    category: "منح دراسية",
    organization: limit(`اتحاد Erasmus Mundus — ${row.acronym}`, 180),
    country: "دول متعددة",
    funding: "FULLY_FUNDED",
    deadline: null,
    deadlineText: "غالبًا أكتوبر–يناير؛ تحقّق من الموعد وتوفر المنحة في موقع البرنامج",
    statusOverride: null,
    alwaysOpen: false,
    featured: false,
    published: true,
    coverImage: `/images/opportunity-${(index % 4) + 1}.svg`,
    officialUrl: row.projectUrl,
    applyUrl: row.consortiumUrl ?? row.projectUrl,
    sourceName: "European Education and Culture Executive Agency (EACEA)",
    sourceUrl: row.projectUrl,
    specialities: [limit(row.title, 255)],
    nationalities: ["جميع الجنسيات"],
    degreeLevels: ["ماجستير"],
  }));
}

function parseEducationUsaList(html) {
  return html
    .split(/<div\b[^>]*class="[^"]*\bviews-row\b[^"]*"[^>]*>/giu)
    .slice(1)
    .map((row) => {
      const link = row.match(
        /<a\b[^>]*href="(\/scholarships\/[^"]+)"[^>]*>([\s\S]*?)<\/a>/iu,
      );
      if (!link) return null;
      return {
        path: decodeHtml(link[1]),
        listTitle: inlineText(link[2]),
        listOrganization: inlineText(divContent(row, "field-hei-institution-name")),
        listDeadline: inlineText(divContent(row, "field-scholarship-deadline")),
      };
    })
    .filter(Boolean);
}

async function loadEducationUsaRows() {
  const firstHtml = await fetchHtml(`${EDUCATION_USA_CATALOGUE}?page=0`);
  const totalMatch = firstHtml.match(
    /<span\b[^>]*class="[^"]*\btotal\b[^"]*"[^>]*>\s*(\d+)\s+Results/iu,
  );
  const lastPageMatch = firstHtml.match(
    /class="pager-last[^"']*"[\s\S]*?href="[^"]*[?&]page=(\d+)/iu,
  );
  if (!totalMatch || !lastPageMatch) {
    throw new Error("Could not discover the EducationUSA catalogue size");
  }
  const expectedTotal = Number.parseInt(totalMatch[1], 10);
  const lastPage = Number.parseInt(lastPageMatch[1], 10);
  const remainingPages = await mapLimit(
    Array.from({ length: lastPage }, (_, index) => index + 1),
    4,
    async (page) => fetchHtml(`${EDUCATION_USA_CATALOGUE}?page=${page}`),
  );
  const rows = [firstHtml, ...remainingPages].flatMap(parseEducationUsaList);
  const uniqueRows = [...new Map(rows.map((row) => [row.path, row])).values()];
  if (uniqueRows.length !== expectedTotal) {
    throw new Error(
      `Expected ${expectedTotal} EducationUSA records but parsed ${uniqueRows.length}`,
    );
  }
  return uniqueRows;
}

async function loadEducationUsaRecords() {
  const rows = await loadEducationUsaRows();
  return rows.map((row, index) => {
    const sourceUrl = new URL(row.path, EDUCATION_USA_CATALOGUE).toString();
    const title = row.listTitle;
    const organization = row.listOrganization || "مؤسسة تعليمية أمريكية";
    const deadlineSource = row.listDeadline;
    const alwaysOpen = isAlwaysOpen(deadlineSource, "");
    const deadline = alwaysOpen ? null : parseLatestExplicitDate(deadlineSource);
    const deadlineText = limit(
      deadlineSource || "تحقّق من الموعد في صفحة المصدر الرسمية",
      120,
    );
    const sourceKey = row.path.replace(/^\/scholarships\//u, "");
    if (!title) throw new Error(`EducationUSA record ${row.path} has no title`);

    return {
      externalKey: limit(`educationusa-${sourceKey}`, 190),
      slug: stableSlug("educationusa", `${sourceKey}-${title}`, row.path),
      title: limit(`منحة أو دعم مالي: ${title}`, 300),
      shortDescription: limit(
        `منحة أو فرصة دعم مالي للطلبة الدوليين لدى ${organization}، مدرجة في قاعدة EducationUSA الرسمية. تحقّق من القيمة والأهلية والموعد في رابط الجهة.`,
        500,
      ),
      description: `نبذة\n${title} فرصة تمويل مدرجة في قاعدة EducationUSA الرسمية للطلبة الدوليين لدى ${organization}.\n\nالتمويل والأهلية\nتختلف قيمة الدعم وشروطه حسب الجهة والبرنامج الأكاديمي. صُنّف التمويل بصورة محافظة في هذه الدفعة، ويجب مراجعة صفحة الجهة للتأكد من المصاريف التي تغطيها المنحة ومتطلبات القبول.\n\nالموعد والتقديم\n${deadlineText}. استخدم رابط الجهة الرسمي واقرأ التعليمات المحدثة قبل إرسال الطلب.`,
      category: "منح دراسية",
      organization: limit(organization, 180),
      country: "الولايات المتحدة",
      funding: fundingType(title),
      deadline,
      deadlineText,
      statusOverride: null,
      alwaysOpen,
      featured: false,
      published: true,
      coverImage: `/images/opportunity-${(index % 4) + 1}.svg`,
      officialUrl: sourceUrl,
      applyUrl: sourceUrl,
      sourceName: "EducationUSA — U.S. Department of State",
      sourceUrl,
      specialities: ["حسب شروط الفرصة"],
      nationalities: ["الطلبة الدوليون"],
      degreeLevels: ["حسب شروط الفرصة"],
    };
  });
}

function validateRecord(record, seen) {
  const required = [
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
  for (const field of required) {
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
    const url = new URL(record[field]);
    if (url.protocol !== "https:") {
      throw new Error(`${field} is not HTTPS for ${record.externalKey}`);
    }
  }
  if (record.alwaysOpen && record.deadline) {
    throw new Error(`alwaysOpen and deadline conflict for ${record.externalKey}`);
  }
  for (const field of ["specialities", "nationalities", "degreeLevels"]) {
    if (!Array.isArray(record[field]) || record[field].length === 0) {
      throw new Error(`${field} is empty for ${record.externalKey}`);
    }
    if (record[field].some((value) => !value || value.length > 255)) {
      throw new Error(`${field} has an invalid value for ${record.externalKey}`);
    }
  }
  const identity = `${record.sourceName}\0${record.externalKey}`;
  if (seen.identities.has(identity)) throw new Error(`Duplicate identity ${identity}`);
  if (seen.slugs.has(record.slug)) throw new Error(`Duplicate slug ${record.slug}`);
  if (seen.officialUrls.has(record.officialUrl.toLowerCase())) {
    throw new Error(`Duplicate official URL ${record.officialUrl}`);
  }
  seen.identities.add(identity);
  seen.slugs.add(record.slug);
  seen.officialUrls.add(record.officialUrl.toLowerCase());
}

async function main() {
  const [eacea, educationUsa] = await Promise.all([
    loadEaceaRecords(),
    loadEducationUsaRecords(),
  ]);
  const records = [...eacea, ...educationUsa];
  const seen = {
    identities: new Set(),
    slugs: new Set(),
    officialUrls: new Set(),
  };
  records.forEach((record) => validateRecord(record, seen));
  const feed = {
    version: `official-scholarships-${SNAPSHOT_DATE}.1`,
    verifiedAt: new Date().toISOString(),
    verifiedBy: "فريق تحرير جامعتي — بيانات وصفية من EACEA وEducationUSA",
    records,
  };
  await writeFile(OUTPUT, `${JSON.stringify(feed, null, 2)}\n`, "utf8");
  process.stdout.write(
    `Wrote ${records.length} records to ${OUTPUT}\n` +
      `EACEA: ${eacea.length}\n` +
      `EducationUSA: ${educationUsa.length}\n`,
  );
}

await main();
