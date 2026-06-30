package com.codeit.mpl.domain.content.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "contents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseUpdatableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "creator_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_contents_creator")
    )
    private User creator;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "content_url", columnDefinition = "TEXT")
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
            foreignKey = @ForeignKey(name = "fk_content_tags_content")
    )
    @Column(name = "tag", nullable = false, length = 255)
    private List<String> tags = new ArrayList<>();

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

        if (tags != null) {
            content.tags.addAll(tags);
        }

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

    public void update(
            String title,
            String description,
            List<String> tags
    ) {
        this.title = title;
        this.description = description;
        this.tags.clear();

        if (tags != null) {
            this.tags.addAll(tags);
        }
    }
}