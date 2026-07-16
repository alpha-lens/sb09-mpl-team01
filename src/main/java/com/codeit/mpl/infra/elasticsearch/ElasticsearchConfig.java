package com.codeit.mpl.infra.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponseInterceptor;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String uris;

    @Value("${spring.elasticsearch.connection-timeout:3s}")
    private java.time.Duration connectionTimeout;

    @Value("${spring.elasticsearch.socket-timeout:3s}")
    private java.time.Duration socketTimeout;

    @Bean
    public RestClient restClient() {
        String[] uriArray = uris.split(",");
        HttpHost[] hosts = new HttpHost[uriArray.length];
        for (int i = 0; i < uriArray.length; i++) {
            String uriString = uriArray[i].trim();
            java.net.URI uri = java.net.URI.create(uriString);
            hosts[i] = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        }

        org.elasticsearch.client.RestClientBuilder builder = RestClient.builder(hosts);
        
        // Apply connection and socket timeouts
        builder.setRequestConfigCallback(requestConfigBuilder -> 
            requestConfigBuilder
                .setConnectTimeout((int) connectionTimeout.toMillis())
                .setSocketTimeout((int) socketTimeout.toMillis())
        );

        return builder
                .setHttpClientConfigCallback(httpClientBuilder -> 
                    httpClientBuilder
                        .addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                            // Spring Boot 3+/4+의 Elasticsearch Java Client가 추가하는 compatible-with 헤더를 제거하고
                            // Elasticsearch 7.10.x(OpenSearch 1.x)가 해석 가능한 application/json으로 강제 치환합니다.
                            org.apache.http.Header[] contentTypeHeaders = request.getHeaders("Content-Type");
                            for (org.apache.http.Header header : contentTypeHeaders) {
                                if (header.getValue().contains("compatible-with")) {
                                    request.setHeader("Content-Type", "application/json");
                                }
                            }
                            org.apache.http.Header[] acceptHeaders = request.getHeaders("Accept");
                            for (org.apache.http.Header header : acceptHeaders) {
                                if (header.getValue().contains("compatible-with")) {
                                    request.setHeader("Accept", "application/json");
                                }
                            }
                        })
                        .addInterceptorLast((HttpResponseInterceptor) (response, context) -> {
                            // Elasticsearch 7.10.x 이하 또는 OpenSearch에서는 X-Elastic-Product 헤더가 없거나 다릅니다.
                            // 최신 Java Client의 제품 검증 로직을 통과하기 위해 응답 헤더에 이를 강제로 셋팅해 줍니다.
                            if (!response.containsHeader("X-Elastic-Product")) {
                                response.addHeader("X-Elastic-Product", "Elasticsearch");
                            }
                        })
                )
                .build();
    }

    @Bean
    public RestClientTransport restClientTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
