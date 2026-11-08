package com.jameati.ai;

import java.text.Normalizer;
import java.util.Locale;

final class AiEvidence {
  private AiEvidence() {}

  static boolean matches(String source, String quote) {
    if (source == null || quote == null || quote.isBlank()) return false;
    if (source.contains(quote)) return true;
    String normalizedQuote=normalize(quote);
    // Very short quotes can occur anywhere and do not establish a claim.
    return normalizedQuote.length()>=12 && normalize(source).contains(normalizedQuote);
  }

  private static String normalize(String value) {
    return Normalizer.normalize(value,Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[\\p{P}\\p{S}\\s]+"," ")
        .trim().replaceAll(" +"," ");
  }
}
