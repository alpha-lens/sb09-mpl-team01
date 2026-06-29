package com.codeit.mpl.domain.content.controller;

import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    /**
     * 콘텐츠 생성
     *
     * TODO(Auth 파트 구현 후 수정)
     * 현재는 인증 사용자 연동이 확정되지 않아 creatorId를 요청 파라미터로 받는다.
     * 추후 @AuthenticationPrincipal 등을 사용해 로그인 사용자 ID를 가져오는 방식으로 변경한다.
     */
    @PostMapping
    public ResponseEntity<ContentDto> createContent(
            @RequestParam UUID creatorId,
            @Valid @RequestBody ContentCreateRequest request
    ) {
        ContentDto response = contentService.createContent(creatorId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * 콘텐츠 단건 조회
     */
    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDto> getContent(
            @PathVariable UUID contentId
    ) {
        ContentDto response = contentService.getContent(contentId);

        return ResponseEntity.ok(response);
    }

    /**
     * 콘텐츠 수정
     */
    @PatchMapping("/{contentId}")
    public ResponseEntity<ContentDto> updateContent(
            @PathVariable UUID contentId,
            @Valid @RequestBody ContentUpdateRequest request
    ) {
        ContentDto response = contentService.updateContent(contentId, request);

        return ResponseEntity.ok(response);
    }

    /**
     * 콘텐츠 삭제
     */
    @DeleteMapping("/{contentId}")
    public ResponseEntity<Void> deleteContent(
            @PathVariable UUID contentId
    ) {
        contentService.deleteContent(contentId);

        return ResponseEntity.noContent().build();
    }

    /**
     * 콘텐츠 목록 조회
     *
     * TODO(Cursor Pagination 구현 보완)
     * 현재 ContentService에서는 cursor, idAfter를 받지만 내부적으로는 PageRequest 기반으로 동작한다.
     * 추후 요구사항에 맞춰 실제 커서 기반 조회로 변경한다.
     */
    @GetMapping
    public ResponseEntity<CursorPageResponseDto<ContentSummary>> getContents(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String idAfter,
            @RequestParam(defaultValue = "20") @Min(1) int limit,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESCENDING") Direction sortDirection
    ) {
        CursorPageResponseDto<ContentSummary> response = contentService.getContents(
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection
        );

        return ResponseEntity.ok(response);
    }
}