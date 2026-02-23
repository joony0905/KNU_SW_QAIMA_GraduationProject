package com.qaima.service.auth;

import com.qaima.common.Blocking;
import com.qaima.domain.User;
import com.qaima.dto.user.LoginRequestDto;
import com.qaima.dto.user.LoginResponseDto;
import com.qaima.dto.user.SignupRequestDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.repository.UserRepository;
import com.qaima.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MailAuthService mailAuthService;
    private final AuthLoginLogService authLoginLogService;

    public Mono<UserResponseDto> signup(SignupRequestDto requestDto, String ip, String ua) {
        final String email = requestDto.getEmail();

        return Blocking.call(() -> userRepository.findByEmail(email))
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        return Mono.error(new IllegalArgumentException("이미 사용 중인 이메일입니다."));
                    }

                    String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

                    User newUser = new User();
                    newUser.setEmail(email);
                    newUser.setPasswordHash(encodedPassword);
                    newUser.setName(requestDto.getName());
                    newUser.setPhone(requestDto.getPhone());
                    newUser.setBirthdate(requestDto.getBirthdate());

                    newUser.setRole(com.qaima.domain.UserRole.user);
                    newUser.setStatus("active"); // status 정책은 유지(로그인 차단은 emailVerified로)
                    newUser.setEmailVerified(false);
                    newUser.setGlossaryHover(false);

                    return Blocking.call(() -> userRepository.save(newUser));
                })
                .flatMap(savedUser ->
                        authLoginLogService.event("SIGNUP_CREATED", true, savedUser.getUserId(), savedUser.getEmail(), ip, ua, null, null)
                                .onErrorResume(e -> Mono.empty())
                                .then(
                                        Blocking.run(() -> mailAuthService.requestEmailVerificationCode(savedUser.getEmail()))
                                                .then(
                                                        authLoginLogService.event("EMAIL_VERIFICATION_REQUESTED", true,
                                                                        savedUser.getUserId(), savedUser.getEmail(), ip, ua, null, null)
                                                                .onErrorResume(e -> Mono.empty())
                                                )
                                                .thenReturn(new UserResponseDto(savedUser))
                                                .onErrorResume(ex ->
                                                        authLoginLogService.event("EMAIL_VERIFICATION_REQUESTED", false,
                                                                        savedUser.getUserId(), savedUser.getEmail(), ip, ua,
                                                                        "EMAIL_SEND_FAILED", ex.getMessage())
                                                                .onErrorResume(e -> Mono.empty())
                                                                .then(Mono.error(ex))
                                                )
                                )
                )
                .onErrorResume(ex ->
                        authLoginLogService.event("SIGNUP_CREATED", false, null, email, ip, ua,
                                        "SIGNUP_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.error(ex))
                );
    }

    public Mono<LoginResponseDto> login(LoginRequestDto requestDto, String ip, String ua) {
        final String email = requestDto.getEmail();

        return Blocking.call(() -> userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.")))
                .flatMap(user -> {
                    if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
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

                    LoginResponseDto dto = new LoginResponseDto(
                            user.getUserId(),
                            user.getEmail(),
                            user.getName(),
                            accessToken
                    );

                    return authLoginLogService.success(user.getUserId(), user.getEmail(), ip, ua)
                            .onErrorResume(e -> Mono.empty())
                            .thenReturn(dto);
                })
                .onErrorResume(ex ->
                        authLoginLogService.failure(email, ip, ua, "AUTH_LOGIN_FAILED", ex.getMessage())
                                .onErrorResume(e -> Mono.empty())
                                .then(Mono.error(ex))
                );
    }
}
