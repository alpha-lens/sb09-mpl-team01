package com.codeit.mpl.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.user.dto.request.UserUpdateRequest;
import com.codeit.mpl.domain.user.dto.request.UserRoleUpdateRequest;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.mapper.UserMapper;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.security.JwtTokenProvider;
import com.codeit.mpl.infra.security.JwtUtil;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.springframework.context.ApplicationEventPublisher;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

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

    @BeforeEach
    void setUpTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDownTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void 이미지_교체_후_커밋되면_이전_이미지가_삭제된다() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().name("우디").profileImageUrl("profile-images/old-key.png").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(binaryContentStorage.put(anyString(), any(MultipartFile.class))).thenReturn("profile-images/new-key.png");
        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", "bytes".getBytes());

        userService.updateUser(userId, new UserUpdateRequest("우디"), image);
        simulateCommit();

        assertThat(user.getProfileImageUrl()).isEqualTo("profile-images/new-key.png");
        verify(binaryContentStorage).delete("profile-images/old-key.png");
    }

    @Test
    void 최초_업로드라_이전_이미지가_없으면_삭제가_호출되지_않는다() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().name("우디").profileImageUrl(null).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(binaryContentStorage.put(anyString(), any(MultipartFile.class))).thenReturn("profile-images/new-key.png");
        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", "bytes".getBytes());

        userService.updateUser(userId, new UserUpdateRequest("우디"), image);
        simulateCommit();

        verify(binaryContentStorage, never()).delete(anyString());
    }

    @Test
    void 이미지_교체_후_롤백되면_새로_업로드한_이미지가_삭제된다() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().name("우디").profileImageUrl("profile-images/old-key.png").build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(binaryContentStorage.put(anyString(), any(MultipartFile.class))).thenReturn("profile-images/new-key.png");
        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", "bytes".getBytes());

        userService.updateUser(userId, new UserUpdateRequest("우디"), image);
        simulateRollback();

        verify(binaryContentStorage).delete("profile-images/new-key.png");
        verify(binaryContentStorage, never()).delete("profile-images/old-key.png");
    }

    @Test
    void 악성_파일명이_들어와도_스토리지_key에_경로_탈출_문자가_섞이지_않는다() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().name("우디").profileImageUrl(null).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(binaryContentStorage.put(keyCaptor.capture(), any(MultipartFile.class))).thenReturn("stored-key");
        MockMultipartFile image = new MockMultipartFile(
                "image", "../../../../etc/passwd.png", "image/png", "bytes".getBytes()
        );

        userService.updateUser(userId, new UserUpdateRequest("우디"), image);

        String capturedKey = keyCaptor.getValue();
        assertThat(capturedKey).doesNotContain("..");
        assertThat(capturedKey).startsWith("profile-images/");
        assertThat(capturedKey).endsWith(".png");
    }

    @Test
    void 파일명에_확장자가_없거나_null이어도_안전하게_처리된다() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().name("우디").profileImageUrl(null).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(binaryContentStorage.put(keyCaptor.capture(), any(MultipartFile.class))).thenReturn("stored-key");
        MockMultipartFile image = new MockMultipartFile("image", null, "image/png", "bytes".getBytes());

        userService.updateUser(userId, new UserUpdateRequest("우디"), image);

        assertThat(keyCaptor.getValue()).startsWith("profile-images/");
    }

    private void simulateCommit() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void simulateRollback() {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    @Test
    void 권한이_변경되면_알림_이벤트가_발행된다() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("test@example.com")
                .name("우디")
                .role(UserRole.USER)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.ADMIN);
        
        UserDto userDto = new UserDto(userId, Instant.now(), "test@example.com", "우디", "profile-images/old-key.png", UserRole.ADMIN, false);
        when(userMapper.toDto(user)).thenReturn(userDto);

        // when
        UserDto result = userService.updateRole(userId, request);

        // then
        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        verify(jwtUtil).deleteRefreshToken(userId);
        verify(eventPublisher).publishEvent(any(com.codeit.mpl.domain.notification.event.NotificationEvent.class));
        assertThat(result).isEqualTo(userDto);
    }

    @Test
    void 권한이_변경되지_않으면_알림_이벤트가_발행되지_않는다() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .email("test@example.com")
                .name("우디")
                .role(UserRole.USER)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.USER);
        
        UserDto userDto = new UserDto(userId, Instant.now(), "test@example.com", "우디", "profile-images/old-key.png", UserRole.USER, false);
        when(userMapper.toDto(user)).thenReturn(userDto);

        // when
        UserDto result = userService.updateRole(userId, request);

        // then
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        verify(jwtUtil).deleteRefreshToken(userId);
        verify(eventPublisher, never()).publishEvent(any(com.codeit.mpl.domain.notification.event.NotificationEvent.class));
        assertThat(result).isEqualTo(userDto);
    }
}
