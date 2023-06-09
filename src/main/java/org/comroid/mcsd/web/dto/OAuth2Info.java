package org.comroid.mcsd.web.dto;

import lombok.Value;

@Value
public class OAuth2Info {
    String name;
    String clientId;
    String secret;
    String scope;
    String redirectUrl;
    String authorizationUrl;
    String tokenUrl;
    String userInfoUrl;
    String userNameAttributeName;
}
