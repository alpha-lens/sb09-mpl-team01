package com.codeit.mpl.domain.review.entity;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "author_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_reviews_author")
  )
  private User author;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "content_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_reviews_content")
  )
  private Content content;

  @Column(name = "text", nullable = false)
  private String text;

  @Column(name = "rating", nullable = false)
  private Double rating;

  public Review(User author, Content content, String text, Double rating) {
    this.author = author;
    this.content = content;
    this.text = text;
    this.rating = rating;
  }

  public void update(String text, Double rating) {
    if (text != null) this.text = text;
    if (rating != null) this.rating = rating;
  }
}