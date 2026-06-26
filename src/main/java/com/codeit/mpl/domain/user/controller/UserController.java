package com.codeit.mpl.domain.user.controller;

import com.codeit.mpl.domain.user.controller.api.UserApi;
import com.codeit.mpl.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mpl.domain.user.dto.request.UserCreateRequest;
import com.codeit.mpl.domain.user.dto.request.UserLockUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserRoleUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController implements UserApi {

    private final UserService userService;

    @PostMapping
    @Override
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @GetMapping
    @Override
    public ResponseEntity<CursorPageResponseDto<UserDto>> findUsers(
            @RequestParam(required = false) String emailLike,
            @RequestParam(required = false) UserRole roleEqual,
            @RequestParam(required = false) Boolean isLocked,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESCENDING") Direction sortDirection) {
        return ResponseEntity.ok(
                userService.findUsers(emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection));
    }

    @GetMapping("/{userId}")
    @Override
    public ResponseEntity<UserDto> findUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @PatchMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Override
    public ResponseEntity<UserDto> updateUser(
            @PathVariable UUID userId,
            @RequestPart("request") @Valid UserUpdateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.ok(userService.updateUser(userId, request, image));
    }

    @PatchMapping("/{userId}/role")
    @Override
    public ResponseEntity<Void> updateUserRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        userService.updateRole(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/locked")
    @Override
    public ResponseEntity<Void> updateUserLocked(
            @PathVariable UUID userId,
            @Valid @RequestBody UserLockUpdateRequest request
    ) {
        userService.updateLock(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/password")
    @Override
    public ResponseEntity<Void> updateUserPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
