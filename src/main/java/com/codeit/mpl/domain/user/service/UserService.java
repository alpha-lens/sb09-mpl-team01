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
import com.codeit.mpl.infra.exception.user.AccountLockedException;
import com.codeit.mpl.infra.exception.user.EmailAlreadyExistsException;
import com.codeit.mpl.infra.exception.user.InvalidCredentialsException;
import com.codeit.mpl.infra.exception.user.InvalidTokenException;
import com.codeit.mpl.infra.exception.user.TemporaryPasswordExpiredException;
import com.codeit.mpl.infra.exception.user.UserNotFoundException;
import com.codeit.mpl.infra.security.JwtTokenProvider;
import com.codeit.mpl.infra.security.JwtUtil;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final BinaryContentStorage binaryContentStorage;
    private final ApplicationEventPublisher eventPublisher;

    private static final String TEMP_PASSWORD_PREFIX = "temporary_password:";
    private static final long TEMP_PASSWORD_TTL_SECONDS = 180;
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    // 원본 파일명은 key에 절대 그대로 넣지 않고, 이 화이트리스트를 통과한 확장자만 뽑아 붙인다.
    private static final Pattern SAFE_EXTENSION_PATTERN = Pattern.compile("\\.[a-zA-Z0-9]{1,10}$");

    public UserDto register(UserCreateRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
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
                .orElseThrow(UserNotFoundException::new);
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (user.isTemporaryPassword()) {
            Boolean hasTempKey = redisTemplate.hasKey(TEMP_PASSWORD_PREFIX + user.getId());
            if (hasTempKey == null || !hasTempKey) {
                throw new TemporaryPasswordExpiredException();
            }
        }
        if (user.isLocked()) {
            throw new AccountLockedException();
        }
        jwtUtil.deleteRefreshToken(user.getId());
        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());
        return new SignInResult(new JwtDto(userMapper.toDto(user), accessToken), refreshToken);
    }

    public void signOut(UUID userId, String accessToken) {
        jwtUtil.deleteRefreshToken(userId);
        if (accessToken != null) {
            try {
                long expirationTime = jwtTokenProvider.getExpirationTime(accessToken);
                jwtUtil.blacklistAccessToken(accessToken, expirationTime);
            } catch (Exception e) {
                log.warn("Failed to blacklist access token on sign-out: {}", e.getMessage());
            }
        }
    }

    public SignInResult refresh(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new InvalidTokenException();
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

        Sort.Direction dir = sortDirection == Direction.ASCENDING ? Sort.Direction.ASC : Sort.Direction.DESC;
        Specification<User> spec = buildFilterSpec(emailLike, roleEqual, isLocked)
                .and(buildCursorSpec(cursor, idAfter, sortBy, dir));

        Sort sort = Sort.by(dir, mapSortBy(sortBy)).and(Sort.by(Sort.Direction.ASC, "id"));

        List<User> users = userRepository.findAll(spec, PageRequest.of(0, limit + 1, sort)).getContent();

        boolean hasNext = users.size() > limit;
        List<User> content = hasNext ? users.subList(0, limit) : users;

        String nextCursor = null;
        String nextIdAfter = null;
        if (hasNext && !content.isEmpty()) {
            User last = content.get(content.size() - 1);
            nextCursor = getCursorValue(last, sortBy);
            nextIdAfter = last.getId().toString();
        }

        long totalCount = userRepository.count(buildFilterSpec(emailLike, roleEqual, isLocked));

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
        String storedKey = null;
        if (image != null && !image.isEmpty()) {
            // DB 접근 전에 업로드를 끝내 트랜잭션이 S3/디스크 I/O를 물고 있지 않게 한다.
            String key = "profile-images/" + userId + "/" + UUID.randomUUID()
                    + extractSafeExtension(image.getOriginalFilename());
            storedKey = binaryContentStorage.put(key, image);
            registerCleanupOnRollback(storedKey);
        }

        User user = findUserById(userId);
        user.updateName(request.name());
        if (storedKey != null) {
            String previousKey = user.getProfileImageUrl();
            user.updateProfileImageUrl(storedKey);
            if (previousKey != null) {
                registerCleanupOnCommit(previousKey);
            }
        }
        return userMapper.toDto(user);
    }

    // 커밋 실패로 트랜잭션이 롤백되면 이미 업로드된 새 파일이 고아로 남으므로 함께 지운다.
    private void registerCleanupOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    binaryContentStorage.delete(key);
                }
            }
        });
    }

    // 새 이미지로 교체하는 커밋이 성공하면, 더 이상 참조되지 않는 이전 파일을 지운다.
    private void registerCleanupOnCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                binaryContentStorage.delete(key);
            }
        });
    }

    // 클라이언트가 보낸 원본 파일명은 절대 신뢰하지 않는다.
    // 경로 구분자("/", "..")가 섞여 있어도 key에 반영되지 않도록, 끝의 확장자만 화이트리스트로 추출한다.
    private String extractSafeExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        Matcher matcher = SAFE_EXTENSION_PATTERN.matcher(originalFilename);
        return matcher.find() ? originalFilename.substring(matcher.start()) : "";
    }

    public UserDto updateRole(UUID userId, UserRoleUpdateRequest request) {
        User user = findUserById(userId);
        UserRole oldRole = user.getRole();
        UserRole newRole = request.role();
        
        user.updateRole(newRole);
        triggerSecurityEvent(userId);

        if (oldRole != newRole) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            User sender = user;
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails userDetails) {
                sender = userRepository.findByEmail(userDetails.getUsername()).orElse(user);
            }

            eventPublisher.publishEvent(new NotificationEvent(
                user,
                sender,
                NotificationLevel.INFO,
                "권한 변경 알림",
                "사용자 권한이 " + oldRole.name() + "에서 " + newRole.name() + "으로 변경되었습니다."
            ));
        }

        return userMapper.toDto(user);
    }

    public UserDto updateLock(UUID userId, UserLockUpdateRequest request) {
        User user = findUserById(userId);
        user.updateLock(request.locked());
        if (request.locked()) {
            triggerSecurityEvent(userId);
        }
        return userMapper.toDto(user);
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = findUserById(userId);
        user.updatePassword(passwordEncoder.encode(request.password()));
        user.clearTemporaryPassword();
        redisTemplate.delete(TEMP_PASSWORD_PREFIX + userId);
        jwtUtil.deleteRefreshToken(userId);
    }

    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(UserNotFoundException::new);
        String tempPassword = generateTempPassword();
        user.updatePassword(passwordEncoder.encode(tempPassword));
        user.markTemporaryPassword();
        redisTemplate.opsForValue().set(
                TEMP_PASSWORD_PREFIX + user.getId(),
                tempPassword,
                TEMP_PASSWORD_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        triggerSecurityEvent(user.getId());
    }

    // 권한 변경/계정 잠금/비밀번호 초기화처럼 기존 세션을 전부 무효화해야 하는 이벤트에서 호출한다.
    // 커밋이 실제로 반영된 뒤에만 Redis에 새 토큰 버전을 반영해야, 롤백 시 잘못된 버전이 캐시되는 것을 막을 수 있다.
    private void triggerSecurityEvent(UUID userId) {
        userRepository.incrementTokenVersion(userId);
        Integer versionObj = userRepository.findTokenVersionById(userId);
        final int newVersion = versionObj != null ? versionObj : 1;

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                jwtUtil.updateTokenVersionInRedis(userId, newVersion);
                                jwtUtil.deleteRefreshToken(userId);
                            } catch (Exception e) {
                                log.error("Failed to sync security event to Redis: {}", e.getMessage());
                            }
                        }
                    }
            );
        } else {
            jwtUtil.updateTokenVersionInRedis(userId, newVersion);
            jwtUtil.deleteRefreshToken(userId);
        }
    }

    @Transactional(readOnly = true)
    public UUID resolveUserId(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(UserNotFoundException::new)
                .getId();
    }

    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private Specification<User> buildFilterSpec(String emailLike, UserRole roleEqual, Boolean isLocked) {
        Specification<User> spec = (root, q, cb) -> cb.conjunction();
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

    private Specification<User> buildCursorSpec(String cursor, UUID idAfter, String sortBy, Sort.Direction dir) {
        if (cursor == null || idAfter == null) {
            return (root, q, cb) -> cb.conjunction();
        }
        return (root, q, cb) -> {
            String field = mapSortBy(sortBy);
            jakarta.persistence.criteria.Expression<String> sortExpr = root.get(field).as(String.class);
            jakarta.persistence.criteria.Expression<String> idExpr = root.get("id").as(String.class);

            jakarta.persistence.criteria.Predicate afterField = dir == Sort.Direction.ASC
                    ? cb.greaterThan(sortExpr, cursor)
                    : cb.lessThan(sortExpr, cursor);
            jakarta.persistence.criteria.Predicate sameFieldAfterId = cb.and(
                    cb.equal(sortExpr, cursor),
                    cb.greaterThan(idExpr, idAfter.toString())
            );
            return cb.or(afterField, sameFieldAfterId);
        };
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
