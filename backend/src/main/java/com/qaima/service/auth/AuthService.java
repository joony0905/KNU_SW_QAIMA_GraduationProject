package com.qaima.service.auth;

import com.qaima.common.Blocking;
import com.qaima.domain.User;
import com.qaima.domain.UserRole;
import com.qaima.dto.user.LoginRequestDto;
import com.qaima.dto.user.LoginResponseDto;
import com.qaima.dto.user.SignupRequestDto;
import com.qaima.dto.user.TokenRefreshResponseDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.repository.UserRepository;
import com.qaima.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long SIGNUP_INITIAL_CREDIT_BALANCE = 5L;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MailAuthService mailAuthService;
    private final AuthLoginLogService authLoginLogService;
    private final LoginSessionService loginSessionService;
    private final TransactionTemplate transactionTemplate;

    public record LoginResult(LoginResponseDto response, String refreshToken) {}

    public record RefreshResult(TokenRefreshResponseDto response, String refreshToken) {}

    public Mono<UserResponseDto> signup(SignupRequestDto requestDto, String ip, String ua) {
        final String email = normalizeEmail(requestDto.getEmail());

        return Blocking.call(() -> {
                    User savedUser = transactionTemplate.execute(status -> createVerifiedUser(requestDto, email));
                    if (savedUser == null) {
                        throw new IllegalStateException("사용자 생성에 실패했습니다.");
                    }
                    return savedUser;
                })
                .flatMap(savedUser ->
                        authLoginLogService.event("SIGNUP_CREATED", true, savedUser.getUserId(), savedUser.getEmail(), ip, ua, null, null)
                                .onErrorResume(e -> Mono.empty())
                                .thenReturn(new UserResponseDto(savedUser))
                )
                .onErrorResume(ex ->
                        authLoginLogService.event("SIGNUP_CREATED", false, null, email, ip, ua,
                                        "SIGNUP_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.error(ex))
                );
    }

    public Mono<LoginResult> login(LoginRequestDto requestDto, String ip, String ua) {
        final String email = normalizeEmail(requestDto.getEmail());

        return Blocking.call(() -> userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.")))
                .flatMap(user -> {
                    if (user.getPasswordHash() == null || !passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
                        return Mono.error(new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));
                    }
                    if (user.getStatus() == null || !user.getStatus().equalsIgnoreCase("active")) {
                        return Mono.error(new IllegalArgumentException("비활성화된 계정입니다."));
                    }
                    if (!user.isEmailVerified()) {
                        return Mono.error(new IllegalArgumentException("이메일 인증이 필요합니다."));
                    }

                    String role = (user.getRole() == null) ? "USER" : user.getRole().name().toUpperCase();
                    String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), role);

                    return loginSessionService.issueRefreshToken(user, ip, ua, null)
                            .flatMap(refreshToken -> {
                                LoginResponseDto dto = new LoginResponseDto(
                                        user.getUserId(),
                                        user.getEmail(),
                                        user.getName(),
                                        accessToken
                                );

                                return authLoginLogService.success(user.getUserId(), user.getEmail(), ip, ua)
                                        .onErrorResume(e -> Mono.empty())
                                        .thenReturn(new LoginResult(dto, refreshToken));
                            });
                })
                .onErrorResume(ex ->
                        authLoginLogService.failure(email, ip, ua, "AUTH_LOGIN_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.error(ex))
                );
    }

    public Mono<RefreshResult> refresh(String refreshToken, String ip, String ua) {
        return loginSessionService.rotateRefreshToken(refreshToken, ip, ua)
                .flatMap(rotated -> {
                    User user = rotated.user();
                    String role = (user.getRole() == null) ? "USER" : user.getRole().name().toUpperCase();
                    String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), role);

                    TokenRefreshResponseDto dto = new TokenRefreshResponseDto(accessToken);

                    return authLoginLogService.event("TOKEN_REFRESHED", true, user.getUserId(), user.getEmail(), ip, ua, null, null)
                            .onErrorResume(e -> Mono.empty())
                            .thenReturn(new RefreshResult(dto, rotated.refreshToken()));
                })
                .onErrorResume(ex ->
                        authLoginLogService.event("TOKEN_REFRESHED", false, null, null, ip, ua,
                                        "TOKEN_REFRESH_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.error(ex))
                );
    }

    public Mono<Void> logout(String refreshToken, String ip, String ua) {
        return loginSessionService.revokeByRefreshToken(refreshToken)
                .then(authLoginLogService.event("LOGOUT", true, null, null, ip, ua, null, null)
                        .onErrorResume(e -> Mono.empty()));
    }

    private User createVerifiedUser(SignupRequestDto requestDto, String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }

        mailAuthService.consumeSignupEmailVerification(email, requestDto.getVerificationCode());

        Instant now = Instant.now();
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        newUser.setName(requestDto.getName());
        newUser.setPhone(requestDto.getPhone());
        newUser.setBirthdate(requestDto.getBirthdate());
        newUser.setRole(UserRole.user);
        newUser.setStatus("active");
        newUser.setEmailVerified(true);
        newUser.setEmailVerifiedAt(now);
        newUser.setGlossaryHover(false);
        newUser.setCreditBalance(SIGNUP_INITIAL_CREDIT_BALANCE);

        return userRepository.save(newUser);
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
