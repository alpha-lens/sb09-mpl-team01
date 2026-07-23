package com.codeit.mpl.domain.content.mapper;

import com.codeit.mpl.domain.content.entity.ContentType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TmdbGenreMapper {

    private static final Map<Integer, String> MOVIE_GENRES = Map.ofEntries(
            Map.entry(28, "액션"),
            Map.entry(12, "모험"),
            Map.entry(16, "애니메이션"),
            Map.entry(35, "코미디"),
            Map.entry(80, "범죄"),
            Map.entry(99, "다큐멘터리"),
            Map.entry(18, "드라마"),
            Map.entry(10751, "가족"),
            Map.entry(14, "판타지"),
            Map.entry(36, "역사"),
            Map.entry(27, "공포"),
            Map.entry(10402, "음악"),
            Map.entry(9648, "미스터리"),
            Map.entry(10749, "로맨스"),
            Map.entry(878, "SF"),
            Map.entry(10770, "TV 영화"),
            Map.entry(53, "스릴러"),
            Map.entry(10752, "전쟁"),
            Map.entry(37, "서부")
    );

    private static final Map<Integer, String> TV_GENRES = Map.ofEntries(
            Map.entry(10759, "액션·모험"),
            Map.entry(16, "애니메이션"),
            Map.entry(35, "코미디"),
            Map.entry(80, "범죄"),
            Map.entry(99, "다큐멘터리"),
            Map.entry(18, "드라마"),
            Map.entry(10751, "가족"),
            Map.entry(10762, "키즈"),
            Map.entry(9648, "미스터리"),
            Map.entry(10763, "뉴스"),
            Map.entry(10764, "리얼리티"),
            Map.entry(10765, "SF·판타지"),
            Map.entry(10766, "연속극"),
            Map.entry(10767, "토크"),
            Map.entry(10768, "전쟁·정치"),
            Map.entry(37, "서부")
    );

    private TmdbGenreMapper() {
    }

    public static List<String> toGenreNames(
            ContentType contentType,
            List<Integer> genreIds
    ) {
        if (genreIds == null || genreIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, String> genreMap = switch (contentType) {
            case MOVIE -> MOVIE_GENRES;
            case TVSERIES -> TV_GENRES;
            case SPORT -> Collections.emptyMap();
        };

        return genreIds.stream()
                .map(genreMap::get)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}