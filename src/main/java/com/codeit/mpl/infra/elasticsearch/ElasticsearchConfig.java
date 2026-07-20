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
                .setHttpClientConfigCallback(httpClientBuilder -> {
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
