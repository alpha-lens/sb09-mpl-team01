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
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.time.Instant;
import java.util.List;

import java.time.temporal.ChronoUnit;

@Getter
@Builder
@Document(indexName = "contents", createIndex = false)
@Setting(settingPath = "/elasticsearch-settings.json")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentDocument {
    @Id
    private String id; // Content UUID String

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori_analyzer", searchAnalyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "autocomplete", type = FieldType.Text, analyzer = "nori_edge_ngram_analyzer", searchAnalyzer = "standard"),
                    @InnerField(suffix = "standard", type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
            }
    )
    private String title;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "keyword")
    private String titleChosung; // 초성 검색용 필드

    @Field(type = FieldType.Text, analyzer = "nori_analyzer", searchAnalyzer = "nori_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String sourceType;

    @Field(type = FieldType.Keyword)
    private String externalId;

    @MultiField(
            mainField = @Field(type = FieldType.Text, analyzer = "nori_analyzer", searchAnalyzer = "nori_analyzer"),
            otherFields = {
                    @InnerField(suffix = "autocomplete", type = FieldType.Text, analyzer = "nori_edge_ngram_analyzer", searchAnalyzer = "standard"),
                    @InnerField(suffix = "standard", type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
            }
    )
    private List<String> tags;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "keyword")
    private List<String> tagsChosung; // 초성 검색용 필드

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Keyword, index = false)
    private String contentUrl;

    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSSX||uuuu-MM-dd'T'HH:mm:ss.SSSX||strict_date_optional_time||epoch_millis")
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
                .createdAt(content.getCreatedAt() != null ? content.getCreatedAt().truncatedTo(ChronoUnit.MILLIS) : null)
                .build();
    }
}
