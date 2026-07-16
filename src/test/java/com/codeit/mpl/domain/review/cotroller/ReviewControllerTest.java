package com.codeit.mpl.domain.review.cotroller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.service.ReviewService;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.infra.exception.GlobalExceptionHandler;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

  // Instant 직렬화를 위해 JavaTimeModule 등록
  private final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(new JavaTimeModule());

  private MockMvc mockMvc;

  @Mock
  private ReviewService reviewService;

  @InjectMocks
  private ReviewController reviewController;

  private UserPrincipal userPrincipal;
  private UUID authorId;

  @BeforeEach
  void setUp() {
    authorId = UUID.randomUUID();
    // UserPrincipal의 int tokenVersion을 반드시 명시 (null이면 ValidationException 400 발생)
    userPrincipal = new UserPrincipal(authorId, "reviewer@example.com", List.of(), 1);

    HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
            && parameter.getParameterType().equals(UserPrincipal.class);
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return userPrincipal;
      }
    };

    mockMvc = MockMvcBuilders.standaloneSetup(reviewController)
        .setCustomArgumentResolvers(principalResolver)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("POST /api/reviews - 리뷰 생성 성공")
  void createReview_success() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "정말 재밌는 영화입니다.", 5);

    UserSummary author = new UserSummary(authorId, "리뷰어", null);
    ReviewDto reviewDto = new ReviewDto(
        reviewId, contentId, author, "정말 재밌는 영화입니다.", 5, Instant.now(), Instant.now()
    );

    given(reviewService.createReview(eq(authorId), any(ReviewCreateRequest.class)))
        .willReturn(reviewDto);

    mockMvc.perform(post("/api/reviews")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reviewId.toString()))
        .andExpect(jsonPath("$.contentId").value(contentId.toString()))
        .andExpect(jsonPath("$.text").value("정말 재밌는 영화입니다."))
        .andExpect(jsonPath("$.rating").value(5));
  }

  @Test
  @DisplayName("PATCH /api/reviews/{reviewId} - 리뷰 수정 성공")
  void updateReview_success() throws Exception {
    UUID contentId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰입니다.", 4);

    UserSummary author = new UserSummary(authorId, "리뷰어", null);
    ReviewDto reviewDto = new ReviewDto(
        reviewId, contentId, author, "수정된 리뷰입니다.", 4, Instant.now(), Instant.now()
    );

    given(reviewService.updateReview(eq(authorId), eq(reviewId), any(ReviewUpdateRequest.class)))
        .willReturn(reviewDto);

    mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reviewId.toString()))
        .andExpect(jsonPath("$.text").value("수정된 리뷰입니다."))
        .andExpect(jsonPath("$.rating").value(4));
  }

  @Test
  @DisplayName("DELETE /api/reviews/{reviewId} - 리뷰 삭제 성공")
  void deleteReview_success() throws Exception {
    UUID reviewId = UUID.randomUUID();

    willDoNothing().given(reviewService).deleteReview(eq(authorId), eq(reviewId));

    mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId))
        .andExpect(status().isNoContent());
  }
}
