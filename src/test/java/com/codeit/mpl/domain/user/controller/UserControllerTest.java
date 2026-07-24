package com.codeit.mpl.domain.user.controller;

import com.codeit.mpl.domain.user.dto.request.*;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.GlobalExceptionHandler;
import com.codeit.mpl.infra.exception.MplException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @Mock
  private UserService userService;

  @InjectMocks
  private UserController userController;

  private UUID userId;
  private String email;
  private String name;
  private UserDto userDto;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    email = "test@test.com";
    name = "테스트유저";
    userDto = new UserDto(userId, Instant.now(), email, name, null, UserRole.USER, false);

    mockMvc = MockMvcBuilders.standaloneSetup(userController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("회원가입 성공")
  void createUser_success() throws Exception {
    UserCreateRequest request = new UserCreateRequest(name, email, "password1234");
    given(userService.register(any(UserCreateRequest.class))).willReturn(userDto);

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.name").value(name));
  }

  @Test
  @DisplayName("회원가입 실패 - 이메일 중복")
  void createUser_fail_emailAlreadyExists() throws Exception {
    UserCreateRequest request = new UserCreateRequest(name, email, "password1234");
    given(userService.register(any(UserCreateRequest.class)))
        .willThrow(new MplException(ErrorCode.EMAIL_ALREADY_EXISTS));

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("사용자 목록 조회 성공")
  void findUsers_success() throws Exception {
    CursorPageResponseDto<UserDto> responseDto = new CursorPageResponseDto<>(
        Collections.singletonList(userDto), null, null, false, 1L, "createdAt", Direction.DESCENDING
    );
    given(userService.findUsers(any(), any(), any(), any(), any(), any(Integer.class), any(), any()))
        .willReturn(responseDto);

    mockMvc.perform(get("/api/users")
            .param("limit", "20")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].email").value(email))
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  @DisplayName("사용자 단건 조회 성공")
  void findUser_success() throws Exception {
    given(userService.getUser(userId)).willReturn(userDto);

    mockMvc.perform(get("/api/users/{userId}", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value(email));
  }

  @Test
  @DisplayName("사용자 단건 조회 실패 - 존재하지 않는 사용자")
  void findUser_fail_userNotFound() throws Exception {
    given(userService.getUser(userId)).willThrow(new MplException(ErrorCode.USER_NOT_FOUND));

    mockMvc.perform(get("/api/users/{userId}", userId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("사용자 정보 수정 성공")
  void updateUser_success() throws Exception {
    UserUpdateRequest request = new UserUpdateRequest("새이름");
    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));
    UserDto updatedDto = new UserDto(userId, Instant.now(), email, "새이름", null, UserRole.USER, false);
    given(userService.updateUser(eq(userId), any(UserUpdateRequest.class), any())).willReturn(updatedDto);

    mockMvc.perform(multipart(HttpMethod.PATCH, "/api/users/{userId}", userId)
            .file(requestPart))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("새이름"));
  }

  @Test
  @DisplayName("권한 변경 성공")
  void updateUserRole_success() throws Exception {
    UserRoleUpdateRequest request = new UserRoleUpdateRequest(UserRole.ADMIN);

    mockMvc.perform(patch("/api/users/{userId}/role", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    then(userService).should().updateRole(userId, request);
  }

  @Test
  @DisplayName("계정 잠금 상태 변경 성공")
  void updateUserLocked_success() throws Exception {
    UserLockUpdateRequest request = new UserLockUpdateRequest(true);

    mockMvc.perform(patch("/api/users/{userId}/locked", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    then(userService).should().updateLock(userId, request);
  }

  @Test
  @DisplayName("비밀번호 변경 성공")
  void updateUserPassword_success() throws Exception {
    ChangePasswordRequest request = new ChangePasswordRequest("newPassword1234");

    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    then(userService).should().changePassword(userId, request);
  }
}
