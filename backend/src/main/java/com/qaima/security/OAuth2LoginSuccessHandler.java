package com.qaima.security;

import com.qaima.service.auth.OAuth2SocialLoginService;
import com.qaima.service.auth.SocialLoginException;
import java.net.InetSocketAddress;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final OAuth2SocialLoginService oAuth2SocialLoginService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final AuthOAuth2Properties authOAuth2Properties;
    private final DefaultServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken oauth2Token)) {
            return redirectFailure(webFilterExchange.getExchange(), "unknown", "OAUTH2_AUTHENTICATION_FAILED");
        }

        String registrationId = oauth2Token.getAuthorizedClientRegistrationId();
        ServerWebExchange exchange = webFilterExchange.getExchange();
        String ip = extractClientIp(exchange);
        String ua = exchange.getRequest().getHeaders().getFirst("User-Agent");

        return oAuth2SocialLoginService.login(registrationId, oauth2Token.getPrincipal().getAttributes(), ip, ua)
                .flatMap(result -> {
                    refreshTokenCookieService.writeRefreshTokenCookie(exchange.getResponse(), result.refreshToken());
                    return clearSessionAndRedirect(exchange, buildSuccessUri(registrationId, result.profileRequired()));
                })
                .onErrorResume(ex -> redirectFailure(exchange, registrationId, resolveCode(ex)));
    }

    private Mono<Void> redirectFailure(ServerWebExchange exchange, String provider, String code) {
        return clearSessionAndRedirect(exchange, buildFailureUri(provider, code));
    }

    private Mono<Void> clearSessionAndRedirect(ServerWebExchange exchange, URI uri) {
        return exchange.getSession()
                .flatMap(WebSession::invalidate)
                .then(redirectStrategy.sendRedirect(exchange, uri));
    }

    private URI buildSuccessUri(String provider, boolean profileRequired) {
        return UriComponentsBuilder.fromUriString(authOAuth2Properties.getSuccessRedirectUrl())
                .queryParam("status", "success")
                .queryParam("provider", provider)
                .queryParam("profileRequired", profileRequired)
                .build(true)
                .toUri();
    }

    private URI buildFailureUri(String provider, String code) {
        return UriComponentsBuilder.fromUriString(authOAuth2Properties.getFailureRedirectUrl())
                .queryParam("status", "error")
                .queryParam("provider", provider)
                .queryParam("code", code)
                .build(true)
                .toUri();
    }

    private static String resolveCode(Throwable throwable) {
        if (throwable instanceof SocialLoginException socialLoginException) {
            return socialLoginException.getCode();
        }
        return "OAUTH2_AUTHENTICATION_FAILED";
    }

    private static String extractClientIp(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return null;
        }
        if (remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        return remoteAddress.getHostString();
    }
}
