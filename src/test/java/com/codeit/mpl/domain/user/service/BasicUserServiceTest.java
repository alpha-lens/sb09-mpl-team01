package com.codeit.mpl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.codeit.mpl.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.domain.user.dto.request.UserCreateRequest;
import com.codeit.mpl.domain.user.dto.request.UserLockUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserRoleUpdateRequest;
import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.mapper.UserMapper;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.security.JwtTokenProvider;
import com.codeit.mpl.infra.security.JwtUtil;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class BasicUserServiceTest {

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserMapper userMapper;

  @Mock
  private JwtUtil jwtUtil;

  @Mock
  private JwtTokenProvider jwtTokenProvider;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private RedisTemplate<String, Object> redisTemplate;

  @Mock
  private BinaryContentStorage binaryContentStorage;

  @InjectMocks
  private UserService userService;

  private UUID userId;
  private String email;
  private String name;
  private String password;
  private String encodedPassword;
  private User user;
  private UserDto userDto;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    email = "test@test.com";
    name = "테스트유저";
    password = "Test1234!";
    encodedPassword = "encodedPassword";

    user = mock(User.class);
    userDto = new UserDto(userId, Instant.now(), email, name, null, UserRole.USER, false);
  }

  @AfterEach
  void tearDownTransactionSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("회원가입 성공")
  void register_success() {
    given(userRepository.existsByEmail(email)).willReturn(false);
    given(passwordEncoder.encode(password)).willReturn(encodedPassword);
    given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
    given(userMapper.toDto(any(User.class))).willReturn(userDto);

    UserDto result = userService.register(new UserCreateRequest(name, email, password));

    assertThat(result).isEqualTo(userDto);
    then(passwordEncoder).should().encode(password);
  }

  @Test
  @DisplayName("회원가입 실패 - 이메일 중복")
  void register_fail_emailAlreadyExists() {
    given(userRepository.existsByEmail(email)).willReturn(true);

    assertThatThrownBy(() -> userService.register(new UserCreateRequest(name, email, password)))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

    then(userRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("로그인 성공")
  void signIn_success() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.getPassword()).willReturn(encodedPassword);
    given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);
    given(user.isTemporaryPassword()).willReturn(false);
    given(user.isLocked()).willReturn(false);
    given(jwtUtil.generateAccessToken(user)).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(userId)).willReturn("refresh-token");
    given(user.getId()).willReturn(userId);
    given(userMapper.toDto(user)).willReturn(userDto);

    SignInResult result = userService.signIn(new SignInRequest(email, password));

    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    assertThat(result.jwtDto().accessToken()).isEqualTo("access-token");
    then(jwtUtil).should().deleteRefreshToken(userId);
  }

  @Test
  @DisplayName("로그인 실패 - 존재하지 않는 사용자")
  void signIn_fail_userNotFound() {
    given(userRepository.findByEmail(email)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.signIn(new SignInRequest(email, password)))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("로그인 실패 - 비밀번호 불일치")
  void signIn_fail_invalidCredentials() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.getPassword()).willReturn(encodedPassword);
    given(passwordEncoder.matches(password, encodedPassword)).willReturn(false);

    assertThatThrownBy(() -> userService.signIn(new SignInRequest(email, password)))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
  }

  @Test
  @DisplayName("로그인 실패 - 잠긴 계정")
  void signIn_fail_accountLocked() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.getPassword()).willReturn(encodedPassword);
    given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);
    given(user.isTemporaryPassword()).willReturn(false);
    given(user.isLocked()).willReturn(true);

    assertThatThrownBy(() -> userService.signIn(new SignInRequest(email, password)))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
  }

  @Test
  @DisplayName("로그인 실패 - 임시 비밀번호 만료")
  void signIn_fail_temporaryPasswordExpired() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.getPassword()).willReturn(encodedPassword);
    given(passwordEncoder.matches(password, encodedPassword)).willReturn(true);
    given(user.isTemporaryPassword()).willReturn(true);
    given(user.getId()).willReturn(userId);
    given(redisTemplate.hasKey(anyString())).willReturn(false);

    assertThatThrownBy(() -> userService.signIn(new SignInRequest(email, password)))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.TEMPORARY_PASSWORD_EXPIRED);
  }

  @Test
  @DisplayName("로그아웃 - AccessToken이 있으면 블랙리스트에 등록한다")
  void signOut_withAccessToken_blacklistsToken() {
    String accessToken = "access-token";
    given(jwtTokenProvider.getExpirationTime(accessToken)).willReturn(System.currentTimeMillis() + 60_000);

    userService.signOut(userId, accessToken);

    then(jwtUtil).should().deleteRefreshToken(userId);
    then(jwtUtil).should().blacklistAccessToken(eq(accessToken), anyLong());
  }

  @Test
  @DisplayName("로그아웃 - AccessToken이 없으면 블랙리스트 등록을 시도하지 않는다")
  void signOut_withoutAccessToken_doesNotBlacklist() {
    userService.signOut(userId, null);

    then(jwtUtil).should().deleteRefreshToken(userId);
    then(jwtUtil).should(never()).blacklistAccessToken(anyString(), anyLong());
    then(jwtTokenProvider).should(never()).getExpirationTime(anyString());
  }

  @Test
  @DisplayName("토큰 재발급 성공")
  void refresh_success() {
    String refreshToken = "old-refresh-token";
    given(jwtUtil.validateRefreshToken(refreshToken)).willReturn(true);
    given(jwtUtil.extractUserIdFromRefreshToken(refreshToken)).willReturn(userId);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(jwtUtil.generateAccessToken(user)).willReturn("new-access-token");
    given(jwtUtil.generateRefreshToken(userId)).willReturn("new-refresh-token");
    given(userMapper.toDto(user)).willReturn(userDto);

    SignInResult result = userService.refresh(refreshToken);

    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    assertThat(result.jwtDto().accessToken()).isEqualTo("new-access-token");
    then(jwtUtil).should().deleteRefreshToken(userId);
  }

  @Test
  @DisplayName("토큰 재발급 실패 - 유효하지 않은 RefreshToken")
  void refresh_fail_invalidToken() {
    given(jwtUtil.validateRefreshToken("bad-token")).willReturn(false);

    assertThatThrownBy(() -> userService.refresh("bad-token"))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_TOKEN);
  }

  @Test
  @DisplayName("사용자 단건 조회 성공")
  void getUser_success() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userMapper.toDto(user)).willReturn(userDto);

    UserDto result = userService.getUser(userId);

    assertThat(result).isEqualTo(userDto);
  }

  @Test
  @DisplayName("사용자 단건 조회 실패 - 존재하지 않는 사용자")
  void getUser_fail_notFound() {
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getUser(userId))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("권한 변경 성공 - 실제 트랜잭션이 없으면 즉시 토큰 버전을 반영한다")
  void updateRole_success_appliesImmediatelyWithoutRealTransaction() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userRepository.findTokenVersionById(userId)).willReturn(2);
    given(userMapper.toDto(user)).willReturn(userDto);

    userService.updateRole(userId, new UserRoleUpdateRequest(UserRole.ADMIN));

    then(user).should().updateRole(UserRole.ADMIN);
    then(userRepository).should().incrementTokenVersion(userId);
    then(jwtUtil).should().updateTokenVersionInRedis(userId, 2);
    then(jwtUtil).should().deleteRefreshToken(userId);
  }

  @Test
  @DisplayName("권한 변경 성공 - 실제 트랜잭션 안에서는 커밋 후에만 Redis에 반영된다")
  void updateRole_success_deferredUntilCommit_insideRealTransaction() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userRepository.findTokenVersionById(userId)).willReturn(2);
    given(userMapper.toDto(user)).willReturn(userDto);

    TransactionSynchronizationManager.setActualTransactionActive(true);
    TransactionSynchronizationManager.initSynchronization();
    try {
      userService.updateRole(userId, new UserRoleUpdateRequest(UserRole.ADMIN));

      then(jwtUtil).should(never()).updateTokenVersionInRedis(any(), anyInt());

      List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
      synchronizations.forEach(TransactionSynchronization::afterCommit);

      then(jwtUtil).should().updateTokenVersionInRedis(userId, 2);
      then(jwtUtil).should().deleteRefreshToken(userId);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
  }

  @Test
  @DisplayName("계정 잠금 성공 - 강제 로그아웃(토큰 버전 증가)이 트리거된다")
  void updateLock_locked_triggersSecurityEvent() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userRepository.findTokenVersionById(userId)).willReturn(2);
    given(userMapper.toDto(user)).willReturn(userDto);

    userService.updateLock(userId, new UserLockUpdateRequest(true));

    then(user).should().updateLock(true);
    then(userRepository).should().incrementTokenVersion(userId);
  }

  @Test
  @DisplayName("계정 잠금 해제 시에는 강제 로그아웃이 트리거되지 않는다")
  void updateLock_unlocked_doesNotTriggerSecurityEvent() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userMapper.toDto(user)).willReturn(userDto);

    userService.updateLock(userId, new UserLockUpdateRequest(false));

    then(user).should().updateLock(false);
    then(userRepository).should(never()).incrementTokenVersion(any());
    then(jwtUtil).should(never()).deleteRefreshToken(any());
  }

  @Test
  @DisplayName("비밀번호 변경 성공")
  void changePassword_success() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(passwordEncoder.encode("newPassword1234")).willReturn("newEncodedPassword");

    userService.changePassword(userId, new ChangePasswordRequest("newPassword1234"));

    then(user).should().updatePassword("newEncodedPassword");
    then(user).should().clearTemporaryPassword();
    then(redisTemplate).should().delete(anyString());
    then(jwtUtil).should().deleteRefreshToken(userId);
  }

  @Test
  @DisplayName("비밀번호 초기화 성공 - 임시 비밀번호 발급과 강제 로그아웃이 함께 처리된다")
  void resetPassword_success_triggersSecurityEvent() {
    ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(passwordEncoder.encode(anyString())).willReturn(encodedPassword);
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(userRepository.findTokenVersionById(userId)).willReturn(2);

    userService.resetPassword(new ResetPasswordRequest(email));

    then(user).should().updatePassword(encodedPassword);
    then(user).should().markTemporaryPassword();
    then(valueOperations).should().set(anyString(), anyString(), anyLong(), any());
    then(userRepository).should().incrementTokenVersion(userId);
    then(jwtUtil).should().deleteRefreshToken(userId);
  }

  @Test
  @DisplayName("사용자 삭제 성공")
  void deleteUser_success() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    userService.deleteUser(userId);

    then(jwtUtil).should().deleteRefreshToken(userId);
    then(userRepository).should().delete(user);
  }

  @Test
  @DisplayName("이메일로 userId 조회 성공")
  void resolveUserId_success() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveUserId(email);

    assertThat(result).isEqualTo(userId);
  }
}
