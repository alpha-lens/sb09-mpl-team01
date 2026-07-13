package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ContentSearchRepository extends ElasticsearchRepository<ContentDocument, String> {

    @Query("{\"bool\": {\"should\": [" +
           "  {\"match\": {\"title\": {\"query\": \"?0\", \"boost\": 3.0}}}," +
           "  {\"match\": {\"tags\": {\"query\": \"?0\", \"boost\": 2.0}}}," +
           "  {\"match\": {\"description\": {\"query\": \"?0\"}}}" +
           "]}}")
    Page<ContentDocument> searchByKeyword(String keyword, Pageable pageable);

    @Query("{\"bool\": {\"should\": [" +
           "  {\"match\": {\"titleChosung\": {\"query\": \"?0\"}}}," +
           "  {\"match\": {\"tagsChosung\": {\"query\": \"?0\"}}}" +
           "]}}")
    Page<ContentDocument> searchByChosung(String chosung, Pageable pageable);
}
