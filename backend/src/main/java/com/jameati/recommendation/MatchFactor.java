package com.jameati.recommendation;

public record MatchFactor(
    String key, int score, int maxScore, boolean matched, String explanation) {}
