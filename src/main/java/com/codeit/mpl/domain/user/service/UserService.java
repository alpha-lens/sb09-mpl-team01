package com.codeit.mpl.domain.user.service;

import com.codeit.mpl.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.domain.user.dto.request.UserCreateRequest;
import com.codeit.mpl.domain.user.dto.request.UserLockUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserRoleUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.mapper.UserMapper;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.common.dto.JwtDto;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String TEMP_PASSWORD_PREFIX = "temporary_password:";
    private static final long TEMP_PASSWORD_TTL_SECONDS = 180;
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    public UserDto register(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new MplException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build();
        return userMapper.toDto(userRepository.save(user));
    }

    public SignInResult signIn(SignInRequest request) {
        User user = userRepository.findByEmail(request.username())
                .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new MplException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLocked()) {
            throw new MplException(ErrorCode.ACCOUNT_LOCKED);
        }
        jwtUtil.deleteRefreshToken(user.getId());
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        return new SignInResult(new JwtDto(userMapper.toDto(user), accessToken), refreshToken);
    }

    public void signOut(UUID userId) {
        jwtUtil.deleteRefreshToken(userId);
    }

    public SignInResult refresh(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new MplException(ErrorCode.INVALID_TOKEN);
        }
        UUID userId = jwtUtil.extractUserIdFromRefreshToken(refreshToken);
        User user = findUserById(userId);
        jwtUtil.deleteRefreshToken(userId);
        String accessToken = jwtUtil.generateAccessToken(user);
        String newRefreshToken = jwtUtil.generateRefreshToken(userId);
        return new SignInResult(new JwtDto(userMapper.toDto(user), accessToken), newRefreshToken);
    }

    @Transactional(readOnly = true)
    public CursorPageResponseDto<UserDto> findUsers(
            String emailLike, UserRole roleEqual, Boolean isLocked,
            String cursor, UUID idAfter, int limit,
            String sortBy, Direction sortDirection) {

        Specification<User> filterSpec = buildFilterSpec(emailLike, roleEqual, isLocked);

        Sort.Direction dir = sortDirection == Direction.ASCENDING ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, mapSortBy(sortBy)).and(Sort.by(Sort.Direction.ASC, "id"));

        List<User> users = userRepository.findAll(filterSpec, PageRequest.of(0, limit + 1, sort)).getContent();

        boolean hasNext = users.size() > limit;
        List<User> content = hasNext ? users.subList(0, limit) : users;

        String nextCursor = null;
        String nextIdAfter = null;
        if (hasNext && !content.isEmpty()) {
            User last = content.get(content.size() - 1);
            nextCursor = getCursorValue(last, sortBy);
            nextIdAfter = last.getId().toString();
        }

        long totalCount = userRepository.count(filterSpec);

        return new CursorPageResponseDto<>(
                content.stream().map(userMapper::toDto).toList(),
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                sortBy,
                sortDirection
        );
    }

    @Transactional(readOnly = true)
    public UserDto getUser(UUID userId) {
        return userMapper.toDto(findUserById(userId));
    }

    public UserDto updateUser(UUID userId, UserUpdateRequest request, MultipartFile image) {
        User user = findUserById(userId);
        user.updateName(request.name());
        if (image != null && !image.isEmpty()) {
            user.updateProfileImageUrl(image.getOriginalFilename());
        }
        return userMapper.toDto(user);
    }

    public UserDto updateRole(UUID userId, UserRoleUpdateRequest request) {
        User user = findUserById(userId);
        user.updateRole(request.role());
        jwtUtil.deleteRefreshToken(userId);
        return userMapper.toDto(user);
    }

    public UserDto updateLock(UUID userId, UserLockUpdateRequest request) {
        User user = findUserById(userId);
        user.updateLock(request.locked());
        if (request.locked()) {
            jwtUtil.deleteRefreshToken(userId);
        }
        return userMapper.toDto(user);
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUserById(userId);
        user.updatePassword(passwordEncoder.encode(request.password()));
        redisTemplate.delete(TEMP_PASSWORD_PREFIX + userId);
    }

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));
        String tempPassword = generateTempPassword();
        user.updatePassword(passwordEncoder.encode(tempPassword));
        redisTemplate.opsForValue().set(
                TEMP_PASSWORD_PREFIX + user.getId(),
                tempPassword,
                TEMP_PASSWORD_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void deleteUser(UUID userId) {
        User user = findUserById(userId);
        jwtUtil.deleteRefreshToken(userId);
        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public UUID resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND))
                .getId();
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));
    }

    private Specification<User> buildFilterSpec(String emailLike, UserRole roleEqual, Boolean isLocked) {
        Specification<User> nullSpec = null;
        Specification<User> spec = Specification.where(nullSpec);
        if (emailLike != null) {
            spec = spec.and((root, q, cb) -> cb.like(root.get("email"), "%" + emailLike + "%"));
        }
        if (roleEqual != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("role"), roleEqual));
        }
        if (isLocked != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("locked"), isLocked));
        }
        return spec;
    }

    private String mapSortBy(String sortBy) {
        return "isLocked".equals(sortBy) ? "locked" : sortBy;
    }

    private String getCursorValue(User user, String sortBy) {
        return switch (sortBy) {
            case "name" -> user.getName();
            case "email" -> user.getEmail();
            case "isLocked" -> String.valueOf(user.isLocked());
            case "role" -> user.getRole().name();
            default -> user.getCreatedAt().toString();
        };
    }

    private String generateTempPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
