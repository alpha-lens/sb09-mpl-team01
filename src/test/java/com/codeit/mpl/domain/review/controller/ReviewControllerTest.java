package com.codeit.mpl.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.service.ReviewService;
import com.codeit.mpl.infra.security.SecurityConfig;
import com.codeit.mpl.infra.security.UserPrincipal;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.codeit.mpl.infra.security.TestSecurityConfig;

@WebMvcTest(
    controllers = ReviewController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, OAuth2ClientAutoConfiguration.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
    }
)
@AutoConfigureMockMvc(addFilters = false)
@Import({ReviewControllerTest.TestConfig.class, TestSecurityConfig.class})
class ReviewControllerTest {

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
  private ReviewService reviewService;

  private UUID authorId;

  @BeforeEach
  void setUp() {
    authorId = UUID.randomUUID();
    UserPrincipal principal = Mockito.mock(UserPrincipal.class);
    when(principal.userId()).thenReturn(authorId);
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
    );
  }

  @Test
  @DisplayName("POST /api/reviews - 리뷰 생성 성공")
  void createReview_success() throws Exception {
    ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "좋아요", 5);
    when(reviewService.createReview(eq(authorId), any())).thenReturn(Mockito.mock(ReviewDto.class));

    mockMvc.perform(post("/api/reviews")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("PATCH /api/reviews/{reviewId} - 리뷰 수정 성공")
  void updateReview_success() throws Exception {
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 텍스트", 4);
    when(reviewService.updateReview(eq(authorId), eq(reviewId), any())).thenReturn(Mockito.mock(ReviewDto.class));

    mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("DELETE /api/reviews/{reviewId} - 리뷰 삭제 성공")
  void deleteReview_success() throws Exception {
    UUID reviewId = UUID.randomUUID();

    mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId)
            .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("GET /api/reviews - 리뷰 목록 조회 성공")
  void getReviews_success() throws Exception {
    UUID contentId = UUID.randomUUID();

    // getReviews는 @ModelAttribute를 사용하므로 query param으로 전달
    mockMvc.perform(get("/api/reviews")
            .param("contentId", contentId.toString())
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk());
  }
}