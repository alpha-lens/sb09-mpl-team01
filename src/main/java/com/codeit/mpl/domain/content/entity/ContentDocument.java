package com.codeit.mpl.domain.content.entity;

import com.codeit.mpl.domain.content.util.HangulUtils;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@Document(indexName = "contents")
@Setting(settingPath = "/elasticsearch-settings.json")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentDocument {
    @Id
    private String id; // Content UUID String

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String titleChosung; // 초성 검색용 필드

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String sourceType;

    @Field(type = FieldType.Keyword)
    private String externalId;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private List<String> tags;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private List<String> tagsChosung; // 초성 검색용 필드

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Keyword, index = false)
    private String contentUrl;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    public static ContentDocument from(Content content) {
        List<String> tags = content.getTags();
        List<String> tagsChosung = tags != null
                ? tags.stream().map(HangulUtils::extractChosung).toList()
                : List.of();

        return ContentDocument.builder()
                .id(content.getId().toString())
                .title(content.getTitle())
                .titleChosung(HangulUtils.extractChosung(content.getTitle()))
                .description(content.getDescription())
                .type(content.getType().name())
                .sourceType(content.getSourceType())
                .externalId(content.getExternalId())
                .tags(tags)
                .tagsChosung(tagsChosung)
                .thumbnailUrl(content.getThumbnailUrl())
                .contentUrl(content.getContentUrl())
                .createdAt(content.getCreatedAt())
                .build();
    }
}
