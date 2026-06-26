package com.codeit.mpl.domain.user.service;

import com.codeit.mpl.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.domain.user.dto.request.UserCreateRequest;
import com.codeit.mpl.domain.user.dto.request.UserLockUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserRoleUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.mapper.UserMapper;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.JwtDto;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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

    public JwtDto signIn(SignInRequest request) {
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
        jwtUtil.generateRefreshToken(user.getId());
        return new JwtDto(userMapper.toDto(user), accessToken);
    }

    public void signOut(UUID userId) {
        jwtUtil.deleteRefreshToken(userId);
    }

    @Transactional(readOnly = true)
    public UserDto getUser(UUID userId) {
        return userMapper.toDto(findUserById(userId));
    }

    public UserDto updateUser(UUID userId, UserUpdateRequest request) {
        User user = findUserById(userId);
        user.updateName(request.name());
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

    private String generateTempPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}
