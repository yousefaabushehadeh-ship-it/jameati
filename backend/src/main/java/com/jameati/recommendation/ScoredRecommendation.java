package com.jameati.recommendation;

import java.util.List;

public record ScoredRecommendation(
    RecommendationCandidate candidate, int matchScore, List<MatchFactor> matchFactors) {}
