package com.codeit.mpl.infra.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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
    private final ObjectMapper objectMapper;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Value("${mpl.frontend.base-url}")
    private String frontendBaseUrl;

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
            /* 기존 csrf 형태인데, 알림이 제대로 가지 않는 것 때문에 일시적으로 비활성화해둠. 나중에 되돌리는 작업이 필요할 수 있음.
              csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                    "/api/auth/sign-in", "/api/auth/sign-out", "/api/auth/reset-password",
                    "/api/auth/refresh", "/api/users"
                )
            * */
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // 인증은 JWT로만 하고 HttpSession은 OAuth2 로그인 handshake 동안만 잠깐 쓰는
            // 임시 저장소라, 로그인 성공 시 세션 ID를 바꾸는 고정 공격 방지가 우리에겐 의미가 없다.
            // 오히려 로그인 콜백 응답과 그 직후 동시에 들어오는 API 요청들이 예전 세션 ID를
            // 참조하면서 "Session was invalidated"(RedisSessionRepository)로 깨지는 원인이었다.
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                    .sessionFixation().none())
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/auth/sign-in", "/api/auth/sign-out", "/api/auth/reset-password",
                    "/api/auth/refresh", "/api/auth/csrf-token").permitAll()
                .requestMatchers(POST, "/api/users").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/", "/index.html", "/favicon.svg", "/static/**", "/assets/**", "/uploads/**","/profile-images/**").permitAll()
                .requestMatchers("/api/notifications/subscribe").permitAll()
                .requestMatchers("/ws/**", "/ws-chat/**").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                .successHandler(oAuth2LoginSuccessHandler)
                .failureHandler(oAuth2LoginFailureHandler)
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, jwtUtil, objectMapper), UsernamePasswordAuthenticationFilter.class);

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

        List<String> allowedOrigins = frontendBaseUrl.isBlank()
                ? List.of("http://localhost:[*]")
                : List.of("http://localhost:[*]", frontendBaseUrl);
        configuration.setAllowedOriginPatterns(allowedOrigins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Cache-Control", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(Arrays.asList("Authorization", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
