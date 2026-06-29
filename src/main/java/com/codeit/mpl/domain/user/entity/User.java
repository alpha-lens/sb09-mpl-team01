package com.codeit.mpl.domain.user.entity;

import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.*;

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

  @Column(name = "is_temporary_password", nullable = false)
  @Builder.Default
  private boolean temporaryPassword = false;

  public void updateName(String name) { this.name = name; }
  public void updatePassword(String encodedPassword) { this.password = encodedPassword; }
  public void updateRole(UserRole role) { this.role = role; }
  public void updateLock(boolean locked) { this.locked = locked; }
  public void updateProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }
  public void markTemporaryPassword() { this.temporaryPassword = true; }
  public void clearTemporaryPassword() { this.temporaryPassword = false; }
}