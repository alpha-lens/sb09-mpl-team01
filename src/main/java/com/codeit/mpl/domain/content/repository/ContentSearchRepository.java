package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.ContentDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ContentSearchRepository extends ElasticsearchRepository<ContentDocument, String> {

    @Query("{\"bool\": {\"should\": [" +
           "  {\"multi_match\": {" +
           "    \"query\": \"?0\"," +
           "    \"fields\": [\"title^3.0\", \"title.autocomplete^2.5\", \"tags^2.0\", \"tags.autocomplete^1.5\", \"description\"]," +
           "    \"operator\": \"AND\"" +
           "  }}," +
           "  {\"multi_match\": {" +
           "    \"query\": \"?0\"," +
           "    \"fields\": [\"title.standard^3.0\", \"tags.standard^2.0\", \"description\"]," +
           "    \"fuzziness\": \"AUTO\"," +
           "    \"operator\": \"AND\"" +
           "  }}" +
           "]}}")
    Page<ContentDocument> searchByKeyword(String keyword, Pageable pageable);

    @Query("{\"bool\": {\"should\": [" +
           "  {\"match\": {\"titleChosung\": {\"query\": \"?0\"}}}," +
           "  {\"match\": {\"tagsChosung\": {\"query\": \"?0\"}}}" +
           "]}}")
    Page<ContentDocument> searchByChosung(String chosung, Pageable pageable);
}
