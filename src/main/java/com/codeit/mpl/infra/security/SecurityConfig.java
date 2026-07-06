package com.codeit.mpl.infra.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpMethod.POST;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtUtil jwtUtil;

    /**
     * Provides a password encoder bean using BCrypt hashing.
     *
     * @return a PasswordEncoder instance using the BCrypt algorithm
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configures HTTP security rules and builds the security filter chain.
     *
     * <p>Authentication via JWT is required for all endpoints except login, signup,
     * logout, notification subscription, and WebSocket chat, which are publicly accessible.
     * CORS is enabled, CSRF protection is disabled, and session management is stateless.</p>
     *
     * @return the configured security filter chain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/sign-in", "/api/auth/sign-out", "/api/auth/reset-password",
                    "/api/auth/refresh", "/api/auth/csrf-token").permitAll()
                .requestMatchers(POST, "/api/users").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/", "/index.html", "/favicon.svg", "/static/**", "/assets/**", "/uploads/**").permitAll()
                .requestMatchers("/api/notifications/subscribe").permitAll()
                .requestMatchers("/ws/**", "/ws-chat/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jwtUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Creates a global CORS configuration for cross-origin requests.
     *
     * @return a CorsConfigurationSource that permits all origins with the specified HTTP methods,
     *         headers, and credential transmission
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        //// 로컬 개발용. 운영 배포 시 실제 도메인 추가 필요
        configuration.setAllowedOriginPatterns(List.of("http://localhost:[*]"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
