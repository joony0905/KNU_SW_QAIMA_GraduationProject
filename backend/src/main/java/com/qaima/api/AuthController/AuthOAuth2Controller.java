package com.qaima.api.AuthController;

import com.qaima.domain.SocialProvider;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Controller
@RequestMapping("/api/v1/auth/oauth2")
public class AuthOAuth2Controller {

    @GetMapping("/{provider}")
    public Mono<Void> redirectToProvider(@PathVariable String provider, ServerWebExchange exchange) {
        SocialProvider socialProvider = SocialProvider.fromRegistrationId(provider)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported social provider"));

        exchange.getResponse().setStatusCode(HttpStatus.FOUND);
        exchange.getResponse().getHeaders().setLocation(
                URI.create("/oauth2/authorization/" + socialProvider.getRegistrationId())
        );
        return exchange.getResponse().setComplete();
    }
}
