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

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserResponseDto signup(SignupRequestDto requestDto) {

        // 이메일 중복 체크
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
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

        User savedUser = userRepository.save(newUser);
        return new UserResponseDto(savedUser);
    }

    /**
     * 로그인: JWT AccessToken 발급
     * - ip는 Controller에서 받아서 넘겨주세요.
     */
    @Transactional
    public LoginResponseDto login(LoginRequestDto requestDto, String ip) {

        User user = userRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다."));

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
    }
}
