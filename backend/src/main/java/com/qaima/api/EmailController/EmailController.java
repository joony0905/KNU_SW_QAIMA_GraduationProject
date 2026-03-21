package com.qaima.api.EmailController;

import com.qaima.common.ApiResponse;
import com.qaima.common.Blocking;
import com.qaima.dto.user.EmailConfirmDto;
import com.qaima.dto.user.EmailRequestDto;
import com.qaima.dto.user.PwdResetRequestDto;
import com.qaima.service.auth.AuthLoginLogService;
import com.qaima.service.auth.MailAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/email")
public class EmailController {

    private final MailAuthService mailAuthService;
    private final AuthLoginLogService authLoginLogService;

    @PostMapping("/verification/request")
    public Mono<ApiResponse<Void>> requestVerification(@Valid @RequestBody EmailRequestDto dto,
                                                       ServerHttpRequest request) {
        String email = dto.getEmail();
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");

        return Blocking.run(() -> mailAuthService.requestEmailVerificationCode(email))
                .then(authLoginLogService.event("EMAIL_VERIFICATION_REQUESTED", true, null, email, ip, ua, null, null)
                        .onErrorResume(e -> Mono.empty()))
                .thenReturn(ApiResponse.<Void>success(null))
                .onErrorResume(ex ->
                        authLoginLogService.event("EMAIL_VERIFICATION_REQUESTED", false, null, email, ip, ua,
                                        "EMAIL_VERIFICATION_REQUEST_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.<ApiResponse<Void>>error(ex))
                );
    }

    @PostMapping("/verification/confirm")
    public Mono<ApiResponse<Void>> confirmVerification(@Valid @RequestBody EmailConfirmDto dto,
                                                       ServerHttpRequest request) {
        String email = dto.getEmail();
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");

        return Blocking.run(() -> mailAuthService.confirmEmailVerificationCode(email, dto.getCode()))
                .then(authLoginLogService.event("EMAIL_VERIFICATION_CONFIRMED", true, null, email, ip, ua, null, null)
                        .onErrorResume(e -> Mono.empty()))
                .thenReturn(ApiResponse.<Void>success(null))
                .onErrorResume(ex ->
                        authLoginLogService.event("EMAIL_VERIFICATION_CONFIRMED", false, null, email, ip, ua,
                                        "EMAIL_VERIFICATION_CONFIRM_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.<ApiResponse<Void>>error(ex))
                );
    }

    @PostMapping("/pwdreset/request")
    public Mono<ApiResponse<Void>> requestPasswordReset(@Valid @RequestBody EmailRequestDto dto,
                                                        ServerHttpRequest request) {
        String email = dto.getEmail();
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");

        return Blocking.run(() -> mailAuthService.requestPasswordResetLink(email))
                .then(authLoginLogService.event("PASSWORD_RESET_REQUESTED", true, null, email, ip, ua, null, null)
                        .onErrorResume(e -> Mono.empty()))
                .thenReturn(ApiResponse.<Void>success(null))
                .onErrorResume(ex ->
                        authLoginLogService.event("PASSWORD_RESET_REQUESTED", false, null, email, ip, ua,
                                        "PASSWORD_RESET_REQUEST_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.<ApiResponse<Void>>error(ex))
                );
    }

    @GetMapping(value = "/pwdreset/form", produces = "text/html; charset=UTF-8")
    public Mono<String> passwordResetForm(@RequestParam("token") String token) {
        return Mono.just("""
            <!doctype html>
            <html lang="ko">
            <head><meta charset="utf-8"><title>비밀번호 재설정</title></head>
            <body>
              <h2>비밀번호 재설정</h2>
              <form method="POST" action="/api/v1/email/pwdreset/confirm-form">
                <input type="hidden" name="token" value="%s"/>
                <label>새 비밀번호(8자 이상)</label><br/>
                <input type="password" name="newPassword" minlength="8" required/><br/><br/>
                <button type="submit">변경</button>
              </form>
            </body>
            </html>
            """.formatted(escapeHtml(token)));
    }

    @PostMapping(
            value = "/pwdreset/confirm-form",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = "text/html; charset=UTF-8"
    )
    public Mono<String> confirmPasswordResetForm(ServerWebExchange exchange,
                                                 ServerHttpRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");

        return exchange.getFormData()
                .defaultIfEmpty(new LinkedMultiValueMap<>())
                .flatMap(form -> {
                    String token = trimToNull(form.getFirst("token"));
                    String newPassword = trimToNull(form.getFirst("newPassword"));

                    if (token == null) {
                        return Mono.error(new IllegalArgumentException("token is required"));
                    }
                    if (newPassword == null) {
                        return Mono.error(new IllegalArgumentException("newPassword is required"));
                    }

                    return Blocking.run(() -> mailAuthService.confirmPasswordReset(token, newPassword));
                })
                .then(authLoginLogService.event("PASSWORD_RESET_CONFIRMED", true, null, null, ip, ua, null, null)
                        .onErrorResume(e -> Mono.empty()))
                .thenReturn("""
                    <!doctype html>
                    <html lang="ko">
                    <head><meta charset="utf-8"><title>완료</title></head>
                    <body><h2>비밀번호가 변경되었습니다.</h2></body>
                    </html>
                    """)
                .onErrorResume(ex ->
                        authLoginLogService.event("PASSWORD_RESET_CONFIRMED", false, null, null, ip, ua,
                                        "PASSWORD_RESET_CONFIRM_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.<String>error(ex))
                );
    }

    @PostMapping("/pwdreset/confirm")
    public Mono<ApiResponse<Void>> confirmPasswordResetJson(@Valid @RequestBody PwdResetRequestDto dto,
                                                            ServerHttpRequest request) {
        String ip = extractClientIp(request);
        String ua = request.getHeaders().getFirst("User-Agent");

        return Blocking.run(() -> mailAuthService.confirmPasswordReset(dto.getToken(), dto.getNewPassword()))
                .then(authLoginLogService.event("PASSWORD_RESET_CONFIRMED", true, null, null, ip, ua, null, null)
                        .onErrorResume(e -> Mono.empty()))
                .thenReturn(ApiResponse.<Void>success(null))
                .onErrorResume(ex ->
                        authLoginLogService.event("PASSWORD_RESET_CONFIRMED", false, null, null, ip, ua,
                                        "PASSWORD_RESET_CONFIRM_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.<ApiResponse<Void>>error(ex))
                );
    }

    private static String extractClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        if (request.getRemoteAddress() == null) return null;
        return request.getRemoteAddress().getAddress().getHostAddress();
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
