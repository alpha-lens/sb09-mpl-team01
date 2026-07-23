package com.codeit.mpl.infra.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
@Profile("!test")
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:29092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:mpl-group}")
    private String groupId;

    @Value("${spring.kafka.properties.security.protocol:}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    // Kafka consumer 연결 안정성 설정
    // OOM 재시작 등으로 consumer가 group에서 빠질 때 리밸런싱이 연속으로 발생하는 것을
    // 완화하기 위해 session timeout을 충분히 크게 설정한다.
    @Value("${spring.kafka.consumer.session-timeout-ms:90000}")
    private int sessionTimeoutMs;

    @Value("${spring.kafka.consumer.heartbeat-interval-ms:30000}")
    private int heartbeatIntervalMs;

    @Value("${spring.kafka.consumer.request-timeout-ms:30000}")
    private int requestTimeoutMs;

    /**
     * 순수 spring-kafka 라이브러리만 사용 중이라(spring-boot-starter-kafka 없음)
     * spring.kafka.properties.*가 자동 바인딩되지 않는다 - 여기서 직접 주입해서 반영한다.
     */
    private void applySecurityProperties(Map<String, Object> configProps) {
        if (!securityProtocol.isBlank()) {
            configProps.put("security.protocol", securityProtocol);
        }
        if (!saslMechanism.isBlank()) {
            configProps.put("sasl.mechanism", saslMechanism);
        }
        if (!saslJaasConfig.isBlank()) {
            configProps.put("sasl.jaas.config", saslJaasConfig);
        }
    }

    /**
     * Configures a Kafka producer factory that serializes keys as strings and values as JSON.
     *
     * @return a producer factory with string key and JSON value serialization
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        applySecurityProperties(configProps);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Provides a Kafka template for sending messages.
     *
     * @return a KafkaTemplate configured for message production with String keys and Object values
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /**
     * Creates a Kafka consumer factory for String-keyed, String-valued messages.
     * JSON-to-POJO 변환은 여기서 하지 않고, 리스너 컨테이너 팩토리의
     * StringJsonMessageConverter가 실제 리스너 파라미터 타입에 맞춰 처리한다
     * (JsonDeserializer로 미리 역직렬화하면 LinkedHashMap이 되어 컨버터가 처리 못함).
     *
     * @return A ConsumerFactory configured to deserialize String keys and values.
     */
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // OOM 재시작 등으로 consumer가 group에서 빠질 때 리밸런싱이 연속 발생하는 문제를 완화한다.
        // session.timeout.ms: broker가 consumer를 dead로 판정하기까지의 시간 (기본 45s → 90s)
        // heartbeat.interval.ms: consumer가 broker에 heartbeat를 보내는 주기 (session의 1/3 이하로 설정)
        // request.timeout.ms: Confluent Cloud 연결 끊김 후 빠른 재연결을 위해 명시
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeoutMs);
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatIntervalMs);
        configProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        applySecurityProperties(configProps);

        return new DefaultKafkaConsumerFactory<>(configProps);
    }


    /**
     * Creates a Kafka listener container factory for consuming Kafka messages.
     *
     * @return a ConcurrentKafkaListenerContainerFactory configured with the consumer factory
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // 원본 값은 String으로만 역직렬화되고, 실제 리스너 파라미터 타입(예: ChatMessage)으로의
        // JSON 변환은 이 컨버터가 담당한다.
        factory.setRecordMessageConverter(new StringJsonMessageConverter());
        return factory;
    }
}
