package com.qaima.security;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements ServerAuthenticationFailureHandler {

    private static final Pattern CALLBACK_PATH = Pattern.compile("/login/oauth2/code/([^/?]+)");
    private static final Pattern AUTHORIZATION_PATH = Pattern.compile("/oauth2/authorization/([^/?]+)");

    private final AuthOAuth2Properties authOAuth2Properties;
    private final DefaultServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange,
                                              AuthenticationException exception) {
        String provider = resolveProvider(webFilterExchange.getExchange().getRequest().getURI().getPath());
        URI uri = UriComponentsBuilder.fromUriString(authOAuth2Properties.getFailureRedirectUrl())
                .queryParam("status", "error")
                .queryParam("provider", provider)
                .queryParam("code", "OAUTH2_AUTHENTICATION_FAILED")
                .build(true)
                .toUri();

        return redirectStrategy.sendRedirect(webFilterExchange.getExchange(), uri);
    }

    private static String resolveProvider(String path) {
        Matcher callbackMatcher = CALLBACK_PATH.matcher(path);
        if (callbackMatcher.find()) {
            return callbackMatcher.group(1);
        }

        Matcher authorizationMatcher = AUTHORIZATION_PATH.matcher(path);
        if (authorizationMatcher.find()) {
            return authorizationMatcher.group(1);
        }

        return "unknown";
    }
}
