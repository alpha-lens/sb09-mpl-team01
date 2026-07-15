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

    @Value("${opensearch.auth-mode:LOCAL}")
    private String authMode;

    @Value("${opensearch.region:ap-northeast-2}")
    private String region;

    @Bean
    public RestClient restClient() {
        String cleanUri = uris.replace("http://", "").replace("https://", "");
        String[] parts = cleanUri.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;
        String scheme = uris.startsWith("https") ? "https" : "http";

        return RestClient.builder(new HttpHost(host, port, scheme))
                .setHttpClientConfigCallback(httpClientBuilder -> {
                    if ("IAM".equalsIgnoreCase(authMode)) {
                        io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor interceptor = 
                            new io.github.acm19.aws.interceptor.http.AwsRequestSigningApacheInterceptor(
                                "es",
                                software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner.create(),
                                software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create(),
                                software.amazon.awssdk.regions.Region.of(region)
                            );
                        httpClientBuilder.addInterceptorLast((HttpRequestInterceptor) interceptor);
                    }

                    httpClientBuilder
                        .addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
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
                            if (!response.containsHeader("X-Elastic-Product")) {
                                response.addHeader("X-Elastic-Product", "Elasticsearch");
                            }
                        });
                    return httpClientBuilder;
                })
                .build();
    }

    @Bean
    public RestClientTransport restClientTransport(RestClient restClient, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper(objectMapper));
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClientTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
