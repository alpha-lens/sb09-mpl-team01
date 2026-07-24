package com.codeit.mpl.domain.content.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(
        name = "contents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_contents_source_external",
                        columnNames = {
                                "source_type",
                                "external_id"
                        }
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseUpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "creator_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_contents_creator"
            )
    )
    private User creator;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(
            name = "thumbnail_url",
            columnDefinition = "TEXT"
    )
    private String thumbnailUrl;

    @Column(
            name = "content_url",
            columnDefinition = "TEXT"
    )
    private String contentUrl;

    @Column(name = "external_id", length = 100)
    private String externalId;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentType type;

    @ElementCollection
    @CollectionTable(
            name = "content_tags",
            joinColumns = @JoinColumn(name = "content_id"),
            foreignKey = @ForeignKey(
                    name = "fk_content_tags_content"
            )
    )
    @Column(
            name = "tag",
            nullable = false,
            length = 255
    )
    private List<String> tags = new ArrayList<>();

    @Column(name = "watcher_count", nullable = false)
    private Long watcherCount = 0L;

    @Column(name = "average_rating", nullable = false)
    private Double averageRating = 0.0;

    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    public void updateWatcherCount(Long watcherCount) {
        this.watcherCount = watcherCount != null ? watcherCount : 0L;
    }

    public void updateReviewStats(Double averageRating, Integer reviewCount) {
        this.averageRating = averageRating != null ? averageRating : 0.0;
        this.reviewCount = reviewCount != null ? reviewCount : 0;
    }


    public static Content create(
            User creator,
            ContentType type,
            String title,
            String description,
            String thumbnailUrl,
            String contentUrl,
            List<String> tags
    ) {
        Content content = new Content();

        content.creator = creator;
        content.type = type;
        content.title = title;
        content.description = description;
        content.thumbnailUrl = thumbnailUrl;
        content.contentUrl = contentUrl;
        content.replaceTags(tags);

        return content;
    }

    public static Content createFromExternalApi(
            User creator,
            ContentType type,
            String title,
            String description,
            String thumbnailUrl,
            String contentUrl,
            String externalId,
            String sourceType,
            List<String> tags
    ) {
        Content content = create(
                creator,
                type,
                title,
                description,
                thumbnailUrl,
                contentUrl,
                tags
        );

        content.externalId = externalId;
        content.sourceType = sourceType;

        return content;
    }

    /*
     * 관리자 직접 수정
     */
    public void update(
            String title,
            String description,
            List<String> tags
    ) {
        this.title = title;
        this.description = description;
        replaceTags(tags);
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    /*
     * TMDB 또는 SportsDB 동기화 시 기존 데이터 갱신
     */
    public void updateFromExternalApi(
            String title,
            String description,
            String thumbnailUrl,
            String contentUrl,
            List<String> tags
    ) {
        this.title = title;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.contentUrl = contentUrl;
        replaceTags(tags);
    }

    private void replaceTags(List<String> tags) {
        this.tags.clear();

        if (tags != null) {
            this.tags.addAll(tags);
        }
    }
}