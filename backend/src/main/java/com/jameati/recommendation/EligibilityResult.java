package com.jameati.recommendation;

import java.util.List;

public record EligibilityResult(boolean eligible, List<String> failures) {
  public static EligibilityResult passed() { return new EligibilityResult(true, List.of()); }
  public static EligibilityResult failed(String reason) {
    return new EligibilityResult(false, List.of(reason));
  }
}
