package org.comroid.mcsd.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.comroid.mcsd.web.MinecraftServerHub;
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
    public ClientRegistrationRepository clientRegistrationRepository() throws IOException {
        var data = new ObjectMapper().readValue(MinecraftServerHub.OAUTH_FILE, OAuth2Info.class);
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("jb-hub")
                .clientId(data.clientId)
                .clientSecret(data.secret)
                .scope(data.scope)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(data.urlBase + "/login/oauth2/code/{registrationId}")
                .authorizationUri(data.hubUrl + "/api/rest/oauth2/auth")
                .tokenUri(data.hubUrl + "/api/rest/oauth2/token")
                .userInfoUri(data.hubUrl + "/api/rest/users/me")
                .userNameAttributeName("login")
                .build());
    }

    private static class OAuth2Info {
        public String clientId;
        public String secret;
        public String scope;
        public String urlBase;
        public String hubUrl;
    }
}
