package com.codeit.mpl.domain.content.controller;

import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentImportRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.dto.response.ExternalContentSearchResult;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    /**
     * 관리자가 콘텐츠를 직접 생성합니다 (multipart/form-data).
     * JSON 파트는 반드시 Content-Type: application/json 으로 전송해야 합니다.
     * (예: new Blob([JSON.stringify(payload)], { type: 'application/json' }))
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContentDto> createContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart("request") @Valid ContentCreateRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail
    ) {
        ContentDto response =
                contentService.createContent(
                        userDetails.getUsername(),
                        request,
                        thumbnail
                );

        return ResponseEntity.ok(response);
    }

    /**
     * 관리자가 외부 API 콘텐츠를 수동 Import합니다.
     */
    @PostMapping("/external/import")
    public ResponseEntity<ContentDto> importExternalContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ContentImportRequest request
    ) {
        ContentDto response =
                contentService.importExternalContent(
                        userDetails.getUsername(),
                        request
                );

        return ResponseEntity.ok(response);
    }

    /**
     * 콘텐츠 단건 조회입니다.
     */
    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDto> getContent(
            @PathVariable UUID contentId
    ) {
        ContentDto response =
                contentService.getContent(contentId);

        return ResponseEntity.ok(response);
    }

    /**
     * 콘텐츠를 수정합니다 (JSON 데이터 및 썸네일 이미지 지원).
     */
    @PatchMapping(value = "/{contentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ContentDto> updateContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID contentId,
            @RequestPart("request") @Valid ContentUpdateRequest request,
            @RequestPart(value = "thumbnail", required = false) MultipartFile thumbnail
    ) {
        ContentDto response =
                contentService.updateContent(
                        userDetails.getUsername(),
                        contentId,
                        request,
                        thumbnail
                );

        return ResponseEntity.ok(response);
    }

    /**
     * 콘텐츠를 삭제합니다.
     */
    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> deleteContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID contentId
    ) {
        contentService.deleteContent(
                userDetails.getUsername(),
                contentId
        );

        return ResponseEntity.noContent().build();
    }

    /**
     * 콘텐츠 목록을 커서 기반으로 조회합니다.
     *
     * 프론트엔드 요청 규격:
     *
     * typeEqual 없음
     * → 전체 콘텐츠 조회
     *
     * typeEqual=movie
     * → 영화만 조회
     *
     * typeEqual=tvSeries
     * → TV 시리즈만 조회
     *
     * typeEqual=sport
     * → 스포츠만 조회
     */
    @GetMapping
    public ResponseEntity<CursorPageResponseDto<ContentSummary>> getContents(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String idAfter,
            @RequestParam(required = false) String keywordLike,
            @RequestParam(required = false) String typeEqual,
            @RequestParam(defaultValue = "20") @Min(1) int limit,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESCENDING") Direction sortDirection
    ) {
        ContentType type =
                convertContentType(typeEqual);

        CursorPageResponseDto<ContentSummary> response =
                contentService.getContents(
                        cursor,
                        idAfter,
                        keywordLike,
                        type,
                        limit,
                        sortBy,
                        sortDirection
                );

        return ResponseEntity.ok(response);
    }

    /**
     * 관리자가 외부 API에서 콘텐츠를 검색합니다.
     *
     * 외부 검색 API는 기존 명세대로 ContentType enum을 직접 사용합니다.
     */
    @GetMapping("/external/search")
    public ResponseEntity<List<ExternalContentSearchResult>>
    searchExternalContents(
            @RequestParam String keyword,
            @RequestParam ContentType type
    ) {
        List<ExternalContentSearchResult> response =
                contentService.searchExternalContents(
                        keyword,
                        type
                );

        return ResponseEntity.ok(response);
    }

    /**
     * 프론트엔드의 typeEqual 값을 ContentType으로 변환합니다.
     *
     * null 또는 빈 문자열은 전체 조회를 의미합니다.
     */
    private ContentType convertContentType(
            String typeEqual
    ) {
        if (typeEqual == null || typeEqual.isBlank()) {
            return null;
        }

        String normalizedType =
                typeEqual.trim();

        return switch (normalizedType) {
            case "movie" ->
                    ContentType.MOVIE;

            case "tvSeries" ->
                    ContentType.TVSERIES;

            case "sport" ->
                    ContentType.SPORT;

            /*
             * Swagger나 다른 클라이언트에서 enum 이름을 그대로 보내는 경우도
             * 허용하도록 대문자 형태를 추가 지원합니다.
             */
            case "MOVIE" ->
                    ContentType.MOVIE;

            case "TVSERIES" ->
                    ContentType.TVSERIES;

            case "SPORT" ->
                    ContentType.SPORT;

            default ->
                    throw new IllegalArgumentException(
                            "typeEqual은 movie, tvSeries, sport만 사용할 수 있습니다."
                    );
        };
    }
}