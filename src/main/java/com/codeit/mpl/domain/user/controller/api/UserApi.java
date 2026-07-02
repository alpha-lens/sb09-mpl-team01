package com.codeit.mpl.domain.user.controller.api;

import com.codeit.mpl.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mpl.domain.user.dto.request.UserCreateRequest;
import com.codeit.mpl.domain.user.dto.request.UserLockUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserRoleUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "사용자 관리", description = "사용자 등록·조회·수정 API")
public interface UserApi {

    @Operation(summary = "사용자 등록 (회원가입)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "409", description = "이메일 중복")
    })
    ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest request);

    @Operation(summary = "[어드민] 사용자 목록 조회", description = "커서 기반 페이지네이션. 이메일·권한·잠금 상태 필터 가능.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요"),
            @ApiResponse(responseCode = "403", description = "권한 없음")
    })
    ResponseEntity<CursorPageResponseDto<UserDto>> findUsers(
            @Parameter(description = "이메일 LIKE 검색") String emailLike,
            @Parameter(description = "권한 필터 (USER/ADMIN)") UserRole roleEqual,
            @Parameter(description = "잠금 상태 필터") Boolean isLocked,
            @Parameter(description = "커서") String cursor,
            @Parameter(description = "커서 보조 ID") UUID idAfter,
            @Parameter(description = "페이지 크기 (기본 20)") int limit,
            @Parameter(description = "정렬 기준") String sortBy,
            @Parameter(description = "정렬 방향") Direction sortDirection
    );

    @Operation(summary = "사용자 상세 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<UserDto> findUser(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId
    );

    @Operation(summary = "프로필 변경", description = "본인의 프로필만 변경할 수 있습니다. multipart/form-data.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<UserDto> updateUser(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId,
            @Valid UserUpdateRequest request,
            MultipartFile image
    );

    @Operation(summary = "프로필 이미지 다운로드", description = "PresignedURL(S3) 또는 파일(local)로 302 리다이렉트/응답합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "리다이렉트 (S3)"),
            @ApiResponse(responseCode = "200", description = "파일 응답 (local)"),
            @ApiResponse(responseCode = "404", description = "프로필 이미지 없음")
    })
    void downloadProfileImage(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId,
            HttpServletResponse response
    );

    @Operation(summary = "[어드민] 권한 수정")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "권한 변경 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "인증 오류"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<Void> updateUserRole(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId,
            @Valid @RequestBody UserRoleUpdateRequest request
    );

    @Operation(summary = "[어드민] 계정 잠금 상태 변경")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "잠금 변경 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "인증 오류"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<Void> updateUserLocked(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId,
            @Valid @RequestBody UserLockUpdateRequest request
    );

    @Operation(summary = "비밀번호 변경", description = "본인의 비밀번호만 변경할 수 있습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "변경 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<Void> updateUserPassword(
            @Parameter(description = "사용자 ID") @PathVariable UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    );
}
