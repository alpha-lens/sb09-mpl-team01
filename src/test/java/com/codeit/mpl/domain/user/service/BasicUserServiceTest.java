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
import com.codeit.mpl.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.AuthProvider;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.mapper.UserMapper;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.security.JwtTokenProvider;
import com.codeit.mpl.infra.security.JwtUtil;
import com.codeit.mpl.infra.security.UserPrincipal;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.context.ApplicationEventPublisher;

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

  @Mock
  private ApplicationEventPublisher eventPublisher;

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
    SecurityContextHolder.clearContext();
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
    given(jwtUtil.extractUserIdFromRefreshToken(refreshToken)).willReturn(userId);
    given(jwtUtil.isValidForRotation(userId, refreshToken)).willReturn(true);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(jwtUtil.generateAccessToken(user)).willReturn("new-access-token");
    given(jwtUtil.rotateRefreshToken(userId, refreshToken)).willReturn("new-refresh-token");
    given(userMapper.toDto(user)).willReturn(userDto);

    SignInResult result = userService.refresh(refreshToken);

    assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
    assertThat(result.jwtDto().accessToken()).isEqualTo("new-access-token");
    then(jwtUtil).should().rotateRefreshToken(userId, refreshToken);
  }

  @Test
  @DisplayName("토큰 재발급 실패 - 유효하지 않은 RefreshToken")
  void refresh_fail_invalidToken() {
    given(jwtUtil.extractUserIdFromRefreshToken("bad-token")).willReturn(userId);
    given(jwtUtil.isValidForRotation(userId, "bad-token")).willReturn(false);

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
    given(user.getRole()).willReturn(UserRole.USER);
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
    given(user.getRole()).willReturn(UserRole.USER);
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
  @DisplayName("이메일로 userId 조회 성공")
  void resolveUserId_success() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveUserId(email);

    assertThat(result).isEqualTo(userId);
  }

  @Test
  @DisplayName("OAuth 사용자 처리 - providerId로 기존 회원을 바로 찾으면 그대로 반환한다")
  void resolveOrCreateOAuthUser_foundByProviderId() {
    given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-123"))
        .willReturn(Optional.of(user));
    given(user.getName()).willReturn(name);
    given(user.isLocked()).willReturn(false);
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveOrCreateOAuthUser(email, name, AuthProvider.KAKAO, "kakao-123");

    assertThat(result).isEqualTo(userId);
    then(user).should(never()).updateName(any());
    then(userRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("OAuth 사용자 처리 - providerId로 찾았는데 닉네임이 바뀌었으면 name을 갱신한다")
  void resolveOrCreateOAuthUser_foundByProviderId_nicknameChanged() {
    String changedName = "테스트유저(변경됨)";
    given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-123"))
        .willReturn(Optional.of(user));
    given(user.getName()).willReturn(name);
    given(user.isLocked()).willReturn(false);
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveOrCreateOAuthUser(email, changedName, AuthProvider.KAKAO, "kakao-123");

    assertThat(result).isEqualTo(userId);
    then(user).should().updateName(changedName);
    then(userRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("OAuth 사용자 처리 - providerId로 못 찾으면 이메일로 찾아 providerId를 채워 넣는다(마이그레이션)")
  void resolveOrCreateOAuthUser_migratesExistingByEmail() {
    given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-123"))
        .willReturn(Optional.empty());
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.isLocked()).willReturn(false);
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveOrCreateOAuthUser(email, name, AuthProvider.KAKAO, "kakao-123");

    assertThat(result).isEqualTo(userId);
    then(user).should().updateProviderId("kakao-123");
    then(userRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("OAuth 사용자 처리 - providerId로도 이메일로도 못 찾으면 새로 가입시킨다")
  void resolveOrCreateOAuthUser_registersNewUser_whenProviderIdGiven() {
    given(userRepository.findByProviderAndProviderId(AuthProvider.KAKAO, "kakao-999"))
        .willReturn(Optional.empty());
    given(userRepository.findByEmail(email)).willReturn(Optional.empty());
    given(passwordEncoder.encode(anyString())).willReturn(encodedPassword);
    given(userRepository.save(any(User.class))).willReturn(user);
    given(user.isLocked()).willReturn(false);
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveOrCreateOAuthUser(email, name, AuthProvider.KAKAO, "kakao-999");

    assertThat(result).isEqualTo(userId);
    then(userRepository).should().save(any(User.class));
  }

  @Test
  @DisplayName("OAuth 사용자 처리 - providerId가 없으면(구글) 이메일로 찾아서 반환한다")
  void resolveOrCreateOAuthUser_providerIdNull_foundByEmail() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.isLocked()).willReturn(false);
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveOrCreateOAuthUser(email, name, AuthProvider.GOOGLE, null);

    assertThat(result).isEqualTo(userId);
    then(userRepository).should(never()).findByProviderAndProviderId(any(), any());
  }

  @Test
  @DisplayName("OAuth 사용자 처리 - providerId가 없고 이메일로도 못 찾으면 새로 가입시킨다")
  void resolveOrCreateOAuthUser_providerIdNull_registersNewUser() {
    given(userRepository.findByEmail(email)).willReturn(Optional.empty());
    given(passwordEncoder.encode(anyString())).willReturn(encodedPassword);
    given(userRepository.save(any(User.class))).willReturn(user);
    given(user.isLocked()).willReturn(false);
    given(user.getId()).willReturn(userId);

    UUID result = userService.resolveOrCreateOAuthUser(email, name, AuthProvider.GOOGLE, null);

    assertThat(result).isEqualTo(userId);
    then(userRepository).should().save(any(User.class));
  }

  @Test
  @DisplayName("OAuth 사용자 처리 실패 - 잠긴 계정")
  void resolveOrCreateOAuthUser_fail_accountLocked() {
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(user.isLocked()).willReturn(true);

    assertThatThrownBy(() -> userService.resolveOrCreateOAuthUser(email, name, AuthProvider.GOOGLE, null))
        .isInstanceOf(MplException.class)
        .extracting(e -> ((MplException) e).getErrorCode())
        .isEqualTo(ErrorCode.ACCOUNT_LOCKED);
  }

  @Test
  @DisplayName("토큰 재발급(관리자용) 성공")
  void issueTokens_success() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(jwtUtil.generateAccessToken(user)).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(userId)).willReturn("refresh-token");
    given(userMapper.toDto(user)).willReturn(userDto);

    SignInResult result = userService.issueTokens(userId);

    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    then(jwtUtil).should().deleteRefreshToken(userId);
  }

  @Test
  @DisplayName("사용자 목록 조회 성공 - 다음 페이지가 없는 경우")
  void findUsers_success_noNextPage() {
    Page<User> page = new PageImpl<>(List.of(user));
    given(userRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);
    given(userRepository.count(any(Specification.class))).willReturn(1L);
    given(userMapper.toDto(user)).willReturn(userDto);

    CursorPageResponseDto<UserDto> result = userService.findUsers(
        null, null, null, null, null, 20, "createdAt", Direction.DESCENDING);

    assertThat(result.data()).containsExactly(userDto);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.totalCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("사용자 목록 조회 성공 - 다음 페이지가 있는 경우 커서를 채워서 반환한다")
  void findUsers_success_hasNextPage() {
    given(user.getId()).willReturn(userId);
    given(user.getCreatedAt()).willReturn(Instant.now());
    User user2 = mock(User.class);
    Page<User> page = new PageImpl<>(List.of(user, user2));
    given(userRepository.findAll(any(Specification.class), any(Pageable.class))).willReturn(page);
    given(userRepository.count(any(Specification.class))).willReturn(2L);
    given(userMapper.toDto(any(User.class))).willReturn(userDto);

    CursorPageResponseDto<UserDto> result = userService.findUsers(
        "test", UserRole.USER, false, "cursor", UUID.randomUUID(), 1, "createdAt", Direction.ASCENDING);

    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextIdAfter()).isNotNull();
  }

  @Test
  @DisplayName("프로필 이미지 없이 정보 수정 - 이미지 업로드/삭제 로직을 타지 않는다")
  void updateUser_withoutImage() {
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userMapper.toDto(user)).willReturn(userDto);

    userService.updateUser(userId, new UserUpdateRequest("새이름"), null);

    then(user).should().updateName("새이름");
    then(binaryContentStorage).should(never()).put(any(), any());
    then(user).should(never()).updateProfileImageUrl(any());
  }

  @Test
  @DisplayName("프로필 이미지와 함께 정보 수정 - 기존 이미지가 없으면 새 이미지만 반영한다")
  void updateUser_withImage_noPreviousImage() {
    MockMultipartFile image = new MockMultipartFile("image", "photo.png", "image/png", "data".getBytes());
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(binaryContentStorage.put(anyString(), any())).willReturn("profile-images/new-key.png");
    given(user.getProfileImageUrl()).willReturn(null);
    given(userMapper.toDto(user)).willReturn(userDto);

    userService.updateUser(userId, new UserUpdateRequest("새이름"), image);

    then(binaryContentStorage).should().put(anyString(), eq(image));
    then(user).should().updateProfileImageUrl("profile-images/new-key.png");
  }

  @Test
  @DisplayName("프로필 이미지와 함께 정보 수정 - 기존 이미지가 있으면 커밋 후 삭제를 예약한다")
  void updateUser_withImage_hasPreviousImage_registersCleanupOnCommit() {
    MockMultipartFile image = new MockMultipartFile("image", "photo.png", "image/png", "data".getBytes());
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(binaryContentStorage.put(anyString(), any())).willReturn("profile-images/new-key.png");
    given(user.getProfileImageUrl()).willReturn("profile-images/old-key.png");
    given(userMapper.toDto(user)).willReturn(userDto);

    TransactionSynchronizationManager.initSynchronization();
    try {
      userService.updateUser(userId, new UserUpdateRequest("새이름"), image);

      then(user).should().updateProfileImageUrl("profile-images/new-key.png");
      List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
      synchronizations.forEach(TransactionSynchronization::afterCommit);
      then(binaryContentStorage).should().delete("profile-images/old-key.png");
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("권한 변경 성공 - 실제 권한 변경이 없으면 알림 이벤트를 발행하지 않는다")
  void updateRole_noActualChange_doesNotPublishEvent() {
    given(user.getRole()).willReturn(UserRole.ADMIN);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userRepository.findTokenVersionById(userId)).willReturn(1);
    given(userMapper.toDto(user)).willReturn(userDto);

    userService.updateRole(userId, new UserRoleUpdateRequest(UserRole.ADMIN));

    then(eventPublisher).should(never()).publishEvent(any());
  }

  @Test
  @DisplayName("권한 변경 성공 - 인증된 관리자가 있으면 그 관리자를 알림 발신자로 사용한다")
  void updateRole_success_usesAuthenticatedUserAsNotificationSender() {
    given(user.getRole()).willReturn(UserRole.USER);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userRepository.findTokenVersionById(userId)).willReturn(2);
    given(userMapper.toDto(user)).willReturn(userDto);

    User adminSender = mock(User.class);
    given(userRepository.findByEmail("admin@test.com")).willReturn(Optional.of(adminSender));

    UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "admin@test.com", List.of());
    Authentication authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
        principal, null, List.of());
    SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
    securityContext.setAuthentication(authentication);
    SecurityContextHolder.setContext(securityContext);

    userService.updateRole(userId, new UserRoleUpdateRequest(UserRole.ADMIN));

    then(userRepository).should().findByEmail("admin@test.com");
    then(eventPublisher).should().publishEvent(any(com.codeit.mpl.domain.notification.event.NotificationEvent.class));
  }
}
