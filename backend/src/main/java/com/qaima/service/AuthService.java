package com.qaima.service;

import com.qaima.domain.User;
import com.qaima.dto.LoginRequestDto;
import com.qaima.dto.LoginResponseDto;
import com.qaima.dto.SignupRequestDto;
import com.qaima.dto.UserResponseDto;
import com.qaima.repository.UserRepository;
import com.qaima.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public Mono<UserResponseDto> signup(SignupRequestDto requestDto) {

        return Mono.fromCallable(() -> userRepository.findByEmail(requestDto.getEmail()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optional -> {
                    if (optional.isPresent()) {
                        return Mono.error(new IllegalArgumentException("이미 사용 중인 이메일입니다."));
                    }

                    // 비밀번호 암호화
                    String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

                    User newUser = new User();
                    newUser.setEmail(requestDto.getEmail());
                    newUser.setPasswordHash(encodedPassword);
                    newUser.setName(requestDto.getName());
                    newUser.setPhone(requestDto.getPhone());
                    newUser.setBirthdate(requestDto.getBirthdate());

                    // Role Status 기본값
                    newUser.setRole(com.qaima.domain.UserRole.user);
                    newUser.setStatus("active");
                    newUser.setEmailVerified(false);
                    newUser.setGlossaryHover(false);

                    return Mono.fromCallable(() -> userRepository.save(newUser))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .map(UserResponseDto::new);
    }

    /**
     * 로그인: JWT AccessToken 발급
     * - ip는 Controller에서 받아서 넘겨주세요.
     */
    @Transactional
    public Mono<LoginResponseDto> login(LoginRequestDto requestDto, String ip) {

        return Mono.fromCallable(() -> userRepository.findByEmail(requestDto.getEmail())
                        .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.")))
                .subscribeOn(Schedulers.boundedElastic())
                .map(user -> {
                    if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
                        throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
                    }

                    // 상태 체크 (inactive/blocked 등)
                    if (user.getStatus() == null || !user.getStatus().equalsIgnoreCase("active")) {
                        throw new IllegalArgumentException("비활성화된 계정입니다.");
                    }

                    // 잠금 체크
                    // if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                    //     throw new IllegalArgumentException("계정이 잠금 상태입니다. 잠시 후 다시 시도하세요.");
                    // }

                    // 마지막 로그인
                    // user.setLastLoginAt(LocalDateTime.now());
                    // user.setLastLoginIp(ip);


                    String role = (user.getRole() == null) ? "USER" : user.getRole().name().toUpperCase();

                    String accessToken = jwtTokenProvider.createAccessToken(user.getUserId(), role);

                    return new LoginResponseDto(
                            user.getUserId(),
                            user.getEmail(),
                            user.getName(),
                            accessToken
                    );
                });
    }
}
