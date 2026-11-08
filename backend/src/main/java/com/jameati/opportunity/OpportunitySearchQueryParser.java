package com.jameati.opportunity;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Interprets common Arabic and English opportunity phrases before text matching. */
final class OpportunitySearchQueryParser {
  record Intent(String country, String category, FundingType funding, String degree,
                List<String> fields, boolean remote, List<String> keywords) {}
  private record Alias(String value, List<String> phrases) {}
  private static Alias alias(String value, String... phrases) { return new Alias(value, List.of(phrases)); }
  private static final List<Alias> COUNTRIES = List.of(
      alias("الأردن", "الأردن", "الاردن", "jordan", "jo"),
      alias("الولايات المتحدة", "الولايات المتحدة الأمريكية", "الولايات المتحدة", "أمريكا", "امريكا", "united states of america", "united states", "america", "usa", "us"),
      alias("ألمانيا", "ألمانيا", "المانيا", "germany", "de"),
      alias("المملكة المتحدة", "المملكة المتحدة", "بريطانيا", "united kingdom", "britain", "uk", "gb"),
      alias("تركيا", "تركيا", "turkey", "turkiye", "tr"),
      alias("كندا", "كندا", "canada", "ca"),
      alias("الإمارات", "الإمارات العربية المتحدة", "الامارات العربية المتحدة", "الإمارات", "الامارات", "دبي", "dubai", "united arab emirates", "uae", "ae"),
      alias("فلسطين", "فلسطين", "palestine", "ps"), alias("فرنسا", "فرنسا", "france", "fr"),
      alias("إسبانيا", "إسبانيا", "اسبانيا", "spain", "es"));
  private static final List<Alias> CATEGORIES = List.of(
      alias("منح دراسية", "منح دراسية", "منحة", "منحه", "منح", "الدراسة", "دراسة", "دراسه", "ادرس", "scholarships", "scholarship", "study"),
      alias("تدريب", "تدريب عملي", "تدريب", "انترنشب", "انترن", "internships", "internship", "training", "intern"),
      alias("وظائف", "وظائف", "وظيفة", "شغل", "عمل", "jobs", "job", "career", "work"),
      alias("تطوع", "تطوع", "volunteering", "volunteer"));
  private static final List<Alias> FUNDING = List.of(
      alias("FULLY_FUNDED", "ممولة بالكامل", "ممول بالكامل", "تمويل كامل", "منحة كاملة", "fully funded", "full funding"),
      alias("PARTIALLY_FUNDED", "ممولة جزئيا", "تمويل جزئي", "partially funded", "partial funding"),
      alias("SELF_FUNDED", "غير ممولة", "تمويل ذاتي", "self funded"));
  private static final List<Alias> DEGREES = List.of(
      alias("بكالوريوس", "بكالوريوس", "بكالوريس", "undergraduate", "bachelors", "bachelor"),
      alias("ماجستير", "ماجستير", "ماستر", "masters", "master"),
      alias("دكتوراه", "دكتوراه", "دكتوراة", "phd", "doctorate"),
      alias("ثانوية عامة", "ثانوية", "high school"));
  private static final List<Alias> FIELDS = List.of(
      alias("programming", "تطوير برمجيات", "هندسة برمجيات", "برمجة", "برمجيات", "software engineering", "software", "programming", "developer", "مبرمجين", "مطورين"),
      alias("engineering", "هندسة", "الهندسة", "engineering"),
      alias("medicine", "الطب", "طب", "medicine", "medical"),
      alias("computer science", "علوم الحاسوب", "حاسوب", "كمبيوتر", "computer science"),
      alias("artificial intelligence", "ذكاء اصطناعي", "artificial intelligence", "ai"),
      alias("business", "إدارة أعمال", "ادارة اعمال", "business administration", "business"));
  private static final Set<String> STOP = Set.of("في", "من", "عن", "على", "الى", "مع", "او", "لي", "بدي", "اريد", "ابحث", "فرص", "فرصة", "in", "at", "for", "the", "a", "an", "to", "of", "show", "find", "search", "opportunities");
  static String normalize(String input) {
    if (input == null) return "";
    String s = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFKC)
        .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "")
        .replaceAll("[أإآٱ]", "ا").replace('ى', 'ي').replace('ة', 'ه')
        .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    return s.replaceAll("\\s+", " ");
  }
  private static String take(String[] text, List<Alias> aliases) {
    List<Map.Entry<String,String>> phrases = new ArrayList<>();
    for (Alias a : aliases) for (String phrase : a.phrases()) phrases.add(Map.entry(normalize(phrase), a.value()));
    phrases.sort((a,b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
    for (var entry : phrases) {
      String target = " " + entry.getKey() + " ";
      String padded = " " + text[0] + " ";
      if (padded.contains(target)) {
        text[0] = normalize(padded.replaceFirst(Pattern.quote(target), " "));
        return entry.getValue();
      }
    }
    return null;
  }
  static Intent parse(String query) {
    String[] text = { normalize(query) };
    boolean remote = take(text, List.of(alias("remote", "عن بعد", "ريموت", "remote", "online"))) != null;
    String country = take(text, COUNTRIES);
    String category = take(text, CATEGORIES);
    String funding = take(text, FUNDING);
    String degree = take(text, DEGREES);
    List<String> fields = new ArrayList<>();
    String field;
    while ((field = take(text, FIELDS)) != null) if (!fields.contains(field)) fields.add(field);
    List<String> keywords = Arrays.stream(text[0].split(" ")).filter(w -> !w.isBlank() && !STOP.contains(w)).toList();
    if (category == null && (funding != null || degree != null)) category = "منح دراسية";
    return new Intent(country, category, funding == null ? null : FundingType.valueOf(funding), degree, fields, remote, keywords);
  }
}
