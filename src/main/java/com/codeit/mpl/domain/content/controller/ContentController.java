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
import java.util.List;
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
        ContentDto response = contentService.createContent(userDetails.getUsername(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/external/import")
    public ResponseEntity<ContentDto> importExternalContent(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ContentImportRequest request
    ) {
        ContentDto response = contentService.importExternalContent(
                userDetails.getUsername(),
                request
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{contentId}")
    public ResponseEntity<ContentDto> getContent(@PathVariable UUID contentId) {
        return ResponseEntity.ok(contentService.getContent(contentId));
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
        contentService.deleteContent(userDetails.getUsername(), contentId);
        return ResponseEntity.noContent().build();
    }

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

    @GetMapping("/external/search")
    public ResponseEntity<List<ExternalContentSearchResult>> searchExternalContents(
            @RequestParam String keyword,
            @RequestParam ContentType type
    ) {
        return ResponseEntity.ok(
                contentService.searchExternalContents(keyword, type)
        );
    }
}