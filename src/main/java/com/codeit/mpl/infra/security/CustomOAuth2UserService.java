package com.codeit.mpl.infra.security;

import com.codeit.mpl.domain.user.entity.AuthProvider;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.exception.user.AccountLockedException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String email;
        String name;
        AuthProvider provider;
        if ("kakao".equals(registrationId)) {
            provider = AuthProvider.KAKAO;
            long kakaoId = ((Number) attributes.get("id")).longValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
            @SuppressWarnings("unchecked")
            Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
            String nickname = (String) profile.get("nickname");
            name = nickname;
            email = nickname + "_" + kakaoId + "@kakao.com";
        } else {
            provider = AuthProvider.GOOGLE;
            email = (String) attributes.get("email");
            name = (String) attributes.get("name");
        }

        try {
            UUID userId = userService.resolveOrCreateOAuthUser(email, name, provider);
            return new CustomOAuth2User(
                    userId,
                    attributes,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
        } catch (AccountLockedException e) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_locked", e.getMessage(), null), e);
        }
    }
}
