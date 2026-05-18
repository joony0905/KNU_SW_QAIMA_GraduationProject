package com.qaima.service.user;

import com.qaima.common.Blocking;
import com.qaima.domain.User;
import com.qaima.dto.user.UserProfileUpdateRequestDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Mono<UserResponseDto> getProfile(Long userId) {
        return loadUser(userId).map(UserResponseDto::new);
    }

    public Mono<UserResponseDto> updateProfile(Long userId, UserProfileUpdateRequestDto requestDto) {
        return loadUser(userId)
                .flatMap(user -> Blocking.call(() -> {
                    String nextName = trimToNull(requestDto.getName());
                    if (nextName != null) {
                        user.setName(nextName);
                    }

                    String nextPhone = normalizePhone(requestDto.getPhone());
                    if (nextPhone != null) {
                        validatePhoneAvailable(nextPhone, user.getUserId());
                        user.setPhone(nextPhone);
                    }

                    String nextExperience = trimToNull(requestDto.getExperience());
                    if (nextExperience != null) {
                        user.setExperience(nextExperience);
                    }

                    if (requestDto.getGlossaryHover() != null) {
                        user.setGlossaryHover(requestDto.getGlossaryHover());
                    }

                    return userRepository.save(user);
                }))
                .map(UserResponseDto::new);
    }

    private Mono<User> loadUser(Long userId) {
        return Blocking.call(() -> userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")));
    }

    private static String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }

    private void validatePhoneAvailable(String phone, Long currentUserId) {
        boolean exists = userRepository.findAllByPhoneIsNotNull().stream()
                .filter(user -> !user.getUserId().equals(currentUserId))
                .map(User::getPhone)
                .map(UserService::normalizePhone)
                .anyMatch(phone::equals);
        if (exists) {
            throw new IllegalArgumentException("이미 사용 중인 전화번호입니다.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
