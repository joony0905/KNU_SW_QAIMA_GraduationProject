package com.qaima.service;

import com.qaima.domain.User;
import com.qaima.domain.UserRole;
import com.qaima.dto.SignupRequestDto;
import com.qaima.dto.UserResponseDto;
import com.qaima.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.qaima.dto.LoginRequestDto;
import com.qaima.dto.LoginResponseDto;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        newUser.setPasswordHash(encodedPassword); // (암호화된 비밀번호 저장)
        newUser.setName(requestDto.getName());
        newUser.setPhone(requestDto.getPhone());
        newUser.setBirthdate(requestDto.getBirthdate());


        newUser.setRole(UserRole.user);
        newUser.setStatus("active");
        newUser.setEmailVerified(false);
        newUser.setGlossaryHover(false);


        // DB에 저장
        User savedUser = userRepository.save(newUser);


        return new UserResponseDto(savedUser);
    }

    @Transactional(readOnly = true)
    public LoginResponseDto login(LoginRequestDto requestDto) {

        // 이메일로 유저 찾기
        User user = userRepository.findByEmail(requestDto.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.")); // 정보에 맞는 유저가 없다면 정보 불일치로 응답)

        // 암호화된 비밀번호 검증
        if (!passwordEncoder.matches(requestDto.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 일치하지 않습니다.");
        }

        // (임시) 로그인 성공
        String tempToken = "임시_JWT_토큰입니다"; // 임시 토큰

        return new LoginResponseDto(
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                tempToken // (임시 토큰 반환)
        );
    }
}
