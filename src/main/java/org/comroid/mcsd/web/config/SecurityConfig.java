package org.comroid.mcsd.web.config;

import org.comroid.mcsd.web.dto.OAuth2Info;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain configure(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests()
                .anyRequest().authenticated().and()
                .csrf().disable()
                .oauth2Login().and()
                .build();
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(@Autowired OAuth2Info info) {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId(info.getName())
                .clientId(info.getClientId())
                .clientSecret(info.getSecret())
                .scope(info.getScope())
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(info.getRedirectUrl())
                .authorizationUri(info.getAuthorizationUrl())
                .tokenUri(info.getTokenUrl())
                .userInfoUri(info.getUserInfoUrl())
                .userNameAttributeName(info.getUserNameAttributeName())
                .build());
    }
}
