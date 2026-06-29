package com.codeit.mpl.domain.content.controlle;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController {

    private final ContentService contentService;

    @PostMapping
    public ResponseEntity<ContentDto> createContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ContentCreateRequest request
    ) {
        ContentDto response = contentService.createContent(
                userDetails.getUsername(),
                request
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDto> getContent(
            @PathVariable UUID contentId
    ) {
        ContentDto response = contentService.getContent(contentId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{contentId}")
    public ResponseEntity<ContentDto> updateContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID contentId,
            @Valid @RequestBody ContentUpdateRequest request
    ) {
        ContentDto response = contentService.updateContent(
                userDetails.getUsername(),
                contentId,
                request
        );

        return ResponseEntity.ok(response);
    }

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
     * 콘텐츠 목록 조회
     *
     * cursor, idAfter를 기준으로 커서 기반 페이지네이션을 수행한다.
     * 첫 페이지 조회 시에는 cursor, idAfter를 생략할 수 있다.
     *
     * 다음 페이지가 존재하는 경우 응답의 nextCursor, nextIdAfter 값을
     * 다음 요청의 cursor, idAfter로 전달하면 된다.
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