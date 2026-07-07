package com.codeit.mpl.domain.review.dto.response;

import java.util.UUID;

public record ReviewStats(UUID contentId, Double averageRating, Long reviewCount) {}