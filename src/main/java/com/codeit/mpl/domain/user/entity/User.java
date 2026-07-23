package com.codeit.mpl.domain.user.entity;

import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User extends BaseUpdatableEntity {

  @Column(nullable = false, unique = true, length = 255)
  private String email;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String password;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "profile_image_url", columnDefinition = "TEXT")
  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private UserRole role = UserRole.USER;

  @Column(name = "is_locked", nullable = false)
  @Builder.Default
  private boolean locked = false;

  @Column(name = "is_temporary_password")
  @Builder.Default
  private Boolean temporaryPassword = false;

  @Column(name = "token_version", nullable = false)
  @Builder.Default
  private int tokenVersion = 1;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @Builder.Default
  private AuthProvider provider = AuthProvider.LOCAL;

  @Column(name = "provider_id", length = 255)
  private String providerId;

  public void updateName(String name) { this.name = name; }
  public void updateProviderId(String providerId) { this.providerId = providerId; }
  public void updatePassword(String encodedPassword) { this.password = encodedPassword; }
  public void updateRole(UserRole role) { this.role = role; }
  public void updateLock(boolean locked) { this.locked = locked; }
  public void updateProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
  public void markTemporaryPassword() { this.temporaryPassword = true; }
  public void clearTemporaryPassword() { this.temporaryPassword = false; }
  public boolean isTemporaryPassword() { return Boolean.TRUE.equals(temporaryPassword); }
}