package com.codeit.mpl.domain.profile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.domain.profile.service.FollowService;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.codeit.mpl.infra.security.SecurityConfig;
import com.codeit.mpl.infra.storage.StorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codeit.mpl.infra.security.TestSecurityConfig;

@WebMvcTest(
    controllers = FollowController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        OAuth2ClientAutoConfiguration.class
    },
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@AutoConfigureMockMvc(addFilters = false) // 보안 필터를 비활성화하고 순수 컨트롤러 로직만 격리 테스트
@Import({FollowControllerTest.TestConfig.class, TestSecurityConfig.class})
class FollowControllerTest {

  @MockitoBean
  private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @TestConfiguration
  static class TestConfig {
    @Bean
    public ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    public StorageProperties storageProperties() {
      return new StorageProperties(
          "local",
          new StorageProperties.Local(".mpl/storage-test"),
          null
      );
    }
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private FollowService followService;

  private UUID followerId;
  private UserPrincipal mockUserPrincipal;

  @BeforeEach
  void setUp() {
    followerId = UUID.randomUUID();

    mockUserPrincipal = Mockito.mock(UserPrincipal.class);
    when(mockUserPrincipal.userId()).thenReturn(followerId);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken(mockUserPrincipal, null, Collections.emptyList())
    );
    SecurityContextHolder.setContext(context);
  }

  @org.junit.jupiter.api.AfterEach
  void tearDown() {
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("POST /api/follows - 팔로우 요청 성공")
  void follow_success() throws Exception {
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    String requestJson = String.format("{\"followeeId\": \"%s\"}", followeeId);

    FollowDto responseDto = new FollowDto(followId, followeeId, followerId);

    when(followService.follow(eq(followerId), any(UUID.class))).thenReturn(responseDto);

    mockMvc.perform(post("/api/follows")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
        .andExpect(jsonPath("$.followerId").value(followerId.toString()));
  }

  @Test
  @DisplayName("GET /api/follows/followed-by-me - 팔로우 중인 경우 데이터 반환")
  void getFollowedByMe_exists() throws Exception {
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();
    FollowDto responseDto = new FollowDto(followId, followeeId, followerId);

    when(followService.getFollowedByMe(followerId, followeeId)).thenReturn(responseDto);

    String expectedJson = objectMapper.writeValueAsString(responseDto);

    mockMvc.perform(get("/api/follows/followed-by-me")
            .param("followeeId", followeeId.toString())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(expectedJson));
  }

  @Test
  @DisplayName("GET /api/follows/followed-by-me - 팔로우 중이 아닌 경우 'null' 문자열 반환")
  void getFollowedByMe_notExists() throws Exception {
    UUID followeeId = UUID.randomUUID();

    when(followService.getFollowedByMe(followerId, followeeId)).thenReturn(null);

    mockMvc.perform(get("/api/follows/followed-by-me")
            .param("followeeId", followeeId.toString())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string("null"));
  }

  @Test
  @DisplayName("GET /api/follows/count - 팔로워 수 조회 성공")
  void getFollowerCount_success() throws Exception {
    UUID followeeId = UUID.randomUUID();
    long expectedCount = 100L;

    when(followService.getFollowerCount(followeeId)).thenReturn(expectedCount);

    mockMvc.perform(get("/api/follows/count")
            .param("followeeId", followeeId.toString())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().string(String.valueOf(expectedCount)));
  }

  @Test
  @DisplayName("DELETE /api/follows/{followId} - 언팔로우 요청 성공")
  void unfollow_success() throws Exception {
    UUID followId = UUID.randomUUID();

    doNothing().when(followService).unfollow(followerId, followId);

    mockMvc.perform(delete("/api/follows/{followId}", followId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());
  }
}