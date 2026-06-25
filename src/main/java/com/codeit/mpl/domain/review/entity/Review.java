package com.codeit.mpl.domain.review.entity;

import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(
    name = "reviews",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"author_id", "content_id"})
    }
)
public class Review extends BaseUpdatableEntity {

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "content_id", nullable = false)
  private UUID contentId;

  @Column(name = "text", nullable = false)
  private String text;

  @Column(name = "rating", nullable = false)
  private Double rating;

  public Review(UUID authorId, UUID contentId, String text, Double rating) {
    this.authorId = authorId;
    this.contentId = contentId;
    this.text = text;
    this.rating = rating;
  }

  public void update(String text, Double rating) {
    if (text != null) this.text = text;
    if (rating != null) this.rating = rating;
  }
}
