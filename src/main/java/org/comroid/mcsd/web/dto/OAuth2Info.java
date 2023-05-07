package org.comroid.mcsd.web.dto;

import lombok.Value;

@Value
public class OAuth2Info {
    String clientId;
    String secret;
    String scope;
    String urlBase;
    String hubUrl;
}
