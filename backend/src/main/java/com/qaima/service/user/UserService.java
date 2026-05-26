package com.qaima.service.user;

import com.qaima.common.Blocking;
import com.qaima.domain.InvestmentLevel;
import com.qaima.domain.User;
import com.qaima.dto.user.SocialProfileCompleteRequestDto;
import com.qaima.dto.user.UserProfileUpdateRequestDto;
import com.qaima.dto.user.UserResponseDto;
import com.qaima.dto.user.UserRiskProfileDto;
import com.qaima.dto.user.UserRiskProfileUpdateRequestDto;
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

    public Mono<UserRiskProfileDto> getRiskProfile(Long userId) {
        return loadUser(userId).map(this::toRiskProfileDto);
    }

    public Mono<UserRiskProfileDto> updateRiskProfile(Long userId, UserRiskProfileUpdateRequestDto requestDto) {
        return loadUser(userId)
                .flatMap(user -> Blocking.call(() -> {
                    user.setDefaultRiskGamma(requestDto.defaultRiskGamma());
                    return userRepository.save(user);
                }))
                .map(this::toRiskProfileDto);
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
                        InvestmentLevel level = InvestmentLevel.fromDisplayName(nextExperience);
                        if (level != null) {
                            user.setInvestmentLevel(level);
                        }
                    }

                    if (requestDto.getGlossaryHover() != null) {
                        user.setGlossaryHover(requestDto.getGlossaryHover());
                    }

                    if (requestDto.getInvestmentLevel() != null) {
                        user.setInvestmentLevel(requestDto.getInvestmentLevel());
                        user.setExperience(requestDto.getInvestmentLevel().getDisplayName());
                    }

                    return userRepository.save(user);
                }))
                .map(UserResponseDto::new);
    }

    public Mono<UserResponseDto> completeSocialProfile(Long userId, SocialProfileCompleteRequestDto requestDto) {
        return loadUser(userId)
                .flatMap(user -> Blocking.call(() -> {
                    if (!canCompleteSocialProfile(user)) {
                        throw new IllegalArgumentException("추가정보 입력 대상 계정이 아닙니다.");
                    }

                    String name = trimToNull(requestDto.getName());
                    String phone = normalizePhone(requestDto.getPhone());
                    String birthdate = normalizeSignupBirthdate(requestDto.getBirthdate());
                    String country = trimToNull(requestDto.getCountry());

                    if (name == null || phone == null || country == null) {
                        throw new IllegalArgumentException("이름, 전화번호, 생년월일, 국적을 입력해 주세요.");
                    }

                    validatePhoneAvailable(phone, user.getUserId());
                    user.setName(name);
                    user.setPhone(phone);
                    user.setBirthdate(birthdate);
                    user.setGender(genderFromResidentDigit(birthdate.charAt(6)));
                    user.setCountry(country);
                    user.setStatus("active");
                    return userRepository.save(user);
                }))
                .map(UserResponseDto::new);
    }

    private Mono<User> loadUser(Long userId) {
        return Blocking.call(() -> userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다.")));
    }

    private UserRiskProfileDto toRiskProfileDto(User user) {
        return new UserRiskProfileDto(
                user.getDefaultRiskGamma(),
                riskProfileType(user.getDefaultRiskGamma())
        );
    }

    private static String riskProfileType(java.math.BigDecimal gamma) {
        if (gamma == null) {
            return null;
        }
        double value = gamma.doubleValue();
        if (value <= 0.20d) return "안정형";
        if (value <= 0.40d) return "안정추구형";
        if (value <= 0.60d) return "위험중립형";
        if (value <= 0.80d) return "적극투자형";
        return "공격투자형";
    }

    private static String normalizePhone(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }

    private static String normalizeSignupBirthdate(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("생년월일은 7자리로 입력해 주세요.");
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() != 7) {
            throw new IllegalArgumentException("생년월일은 7자리로 입력해 주세요.");
        }
        char genderDigit = digits.charAt(6);
        if (genderDigit < '1' || genderDigit > '4') {
            throw new IllegalArgumentException("올바른 성별 자릿수를 입력해 주세요.");
        }
        return digits;
    }

    private static String genderFromResidentDigit(char genderDigit) {
        return (genderDigit == '1' || genderDigit == '3') ? "male" : "female";
    }

    private static boolean canCompleteSocialProfile(User user) {
        return (user.getStatus() != null && user.getStatus().equalsIgnoreCase("profile_required"))
                || !StringUtils.hasText(user.getPhone())
                || !StringUtils.hasText(user.getBirthdate())
                || !StringUtils.hasText(user.getGender())
                || !StringUtils.hasText(user.getCountry());
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
