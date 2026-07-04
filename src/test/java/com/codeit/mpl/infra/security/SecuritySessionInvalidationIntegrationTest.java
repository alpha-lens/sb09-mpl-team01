package com.codeit.mpl.infra.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mpl.domain.user.dto.request.ChangePasswordRequest;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.domain.user.service.UserService;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

@ActiveProfiles("test")
@SpringBootTest
public class SecuritySessionInvalidationIntegrationTest {

    private MockMvc mockMvc;

    @MockitoBean
    private com.codeit.mpl.domain.chat.service.WatchingSessionService watchingSessionService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @MockitoSpyBean
    private RedisTemplate<String, Object> redisTemplate;

    private ValueOperations<String, Object> valueOperations;

    private User testUser;
    private String validAccessToken;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        valueOperations = mock(ValueOperations.class);
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        doReturn(Collections.emptySet()).when(redisTemplate).keys(anyString());
        doReturn(false).when(redisTemplate).hasKey(anyString());
        doReturn(true).when(redisTemplate).delete(anyString());
        doReturn(0L).when(redisTemplate).delete(anyCollection());

        // Clean up database to avoid conflicts
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            entityManager.createNativeQuery("DELETE FROM direct_message").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM conversation").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM notifications").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM reviews").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM follows").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM playlist_contents").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM playlist_subscriptions").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM playlists").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM content_tags").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM watching_sessions").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM contents").executeUpdate();
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            return null;
        });

        testUser = User.builder()
                .email("invalidation-test@example.com")
                .password(passwordEncoder.encode("password123"))
                .name("Test User")
                .role(UserRole.USER)
                .locked(false)
                .tokenVersion(1)
                .build();
        testUser = userRepository.save(testUser);

        validAccessToken = jwtTokenProvider.createToken(
                testUser.getEmail(),
                "ROLE_" + testUser.getRole().name(),
                testUser.getId().toString(),
                testUser.getTokenVersion()
        );
    }

    @Test
    void testRedisCacheMissFallback() {
        // Given: Redis returns null (Cache Miss)
        String versionKey = "user:token_version:" + testUser.getId();
        when(valueOperations.get(versionKey)).thenReturn(null);

        // When: Query version
        int version = jwtUtil.getCurrentTokenVersion(testUser.getId());

        // Then: DB version returned, and cached in Redis with TTL
        assertThat(version).isEqualTo(testUser.getTokenVersion());
        verify(valueOperations, times(1)).set(eq(versionKey), eq(testUser.getTokenVersion()), anyLong(), any());
    }

    @Test
    void testJpaCacheClearAfterBulkUpdate() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            // Given: Fetch User to 1st level cache
            User userBefore = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(userBefore.getTokenVersion()).isEqualTo(1);

            // When: Bulk update version
            userRepository.incrementTokenVersion(testUser.getId());

            // Then: Fetch again. Because clearAutomatically=true, it should fetch updated value 2
            User userAfter = userRepository.findById(testUser.getId()).orElseThrow();
            assertThat(userAfter.getTokenVersion()).isEqualTo(2);
            return null;
        });
    }

    @Test
    void testSecurityEventPostCommitSynchronization() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // Given: Run inside transaction
        tx.execute(status -> {
            ChangePasswordRequest request = new ChangePasswordRequest("newpassword123");
            userService.changePassword(testUser.getId(), request);

            // Redis updates should NOT be called before commit
            String versionKey = "user:token_version:" + testUser.getId();
            verify(valueOperations, never()).set(eq(versionKey), any(), anyLong(), any());
            verify(redisTemplate, never()).delete(eq("refresh_token:" + testUser.getId()));
            return null;
        });

        // Then: After commit, Redis version is updated and RT is deleted
        String versionKey = "user:token_version:" + testUser.getId();
        verify(valueOperations, times(1)).set(eq(versionKey), eq(2), anyLong(), any());
        verify(redisTemplate, times(1)).delete(eq("refresh_token:" + testUser.getId()));
    }

    @Test
    void testSecurityEventRollbackDoesNotSyncToRedis() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        try {
            tx.execute(status -> {
                ChangePasswordRequest request = new ChangePasswordRequest("newpassword123");
                userService.changePassword(testUser.getId(), request);
                throw new RuntimeException("Rollback simulation");
            });
        } catch (Exception e) {
            // Expected rollback
        }

        // Then: Redis operations are never performed
        String versionKey = "user:token_version:" + testUser.getId();
        verify(valueOperations, never()).set(eq(versionKey), any(), anyLong(), any());
        verify(redisTemplate, never()).delete(eq("refresh_token:" + testUser.getId()));
    }

    @Test
    void testTokenVersionMismatchRejectsRequest() throws Exception {
        // Given: Redis says current version is 2, but token version is 1
        String versionKey = "user:token_version:" + testUser.getId();
        when(valueOperations.get(versionKey)).thenReturn(2);

        // When & Then: Request with stale token is unauthorized (SecurityContext not set, returns 401)
        mockMvc.perform(get("/api/notifications?limit=10")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testTokenVersionMatchPassesRequest() throws Exception {
        // Given: Redis version matches token version (1)
        String versionKey = "user:token_version:" + testUser.getId();
        when(valueOperations.get(versionKey)).thenReturn(1);

        // When & Then: Request passes version check
        mockMvc.perform(get("/api/notifications?limit=10")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void testTokenVersionGreaterAnomalyWarning() throws Exception {
        // Given: Token version is 2, but Redis says version is 1 (Anomaly)
        String tokenVersion2 = jwtTokenProvider.createToken(
                testUser.getEmail(),
                "ROLE_" + testUser.getRole().name(),
                testUser.getId().toString(),
                2
        );
        String versionKey = "user:token_version:" + testUser.getId();
        when(valueOperations.get(versionKey)).thenReturn(1);

        // When & Then: Request still passes (Fail-safe for minor cache lag)
        mockMvc.perform(get("/api/notifications?limit=10")
                        .header("Authorization", "Bearer " + tokenVersion2))
                .andExpect(status().isOk());
    }

    @Test
    void testRedisOutageFailOpenForAccessToken() throws Exception {
        // Given: Redis throws exception (Outage)
        doThrow(new RedisConnectionFailureException("Connection refused")).when(redisTemplate).hasKey(anyString());
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Connection refused"));

        // When & Then: Request still passes because Access Token uses Fail-Open
        mockMvc.perform(get("/api/notifications?limit=10")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk());
    }

    @Test
    void testConcurrencySafetyForVersionIncrements() throws Exception {
        int threads = 10;
        ExecutorService service = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            service.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TransactionTemplate tx = new TransactionTemplate(transactionManager);
                    tx.execute(status -> {
                        userRepository.incrementTokenVersion(testUser.getId());
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for all threads to be ready
        readyLatch.await();
        // Start all threads simultaneously
        startLatch.countDown();
        // Wait for completion
        doneLatch.await();
        service.shutdown();

        // Then: DB version should be exactly 1 + 10 = 11 (No lost updates)
        Integer finalVersion = userRepository.findTokenVersionById(testUser.getId());
        assertThat(finalVersion).isEqualTo(11);
    }
}
