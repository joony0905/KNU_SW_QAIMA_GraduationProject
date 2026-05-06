package com.qaima.service.auth;

import com.qaima.common.Blocking;
import com.qaima.domain.SocialAccount;
import com.qaima.domain.SocialProvider;
import com.qaima.domain.User;
import com.qaima.domain.UserRole;
import com.qaima.repository.SocialAccountRepository;
import com.qaima.repository.UserRepository;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OAuth2SocialLoginService {

    private static final long SOCIAL_SIGNUP_INITIAL_CREDIT_BALANCE = 5L;

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final LoginSessionService loginSessionService;
    private final AuthLoginLogService authLoginLogService;
    private final PlatformTransactionManager transactionManager;

    public record SocialLoginResult(String refreshToken) {}

    private record SocialProfile(
            SocialProvider provider,
            String providerUserId,
            String email,
            String name,
            boolean emailVerified
    ) {}

    public Mono<SocialLoginResult> login(String registrationId,
                                         Map<String, Object> attributes,
                                         String ip,
                                         String ua) {
        return Mono.defer(() -> {
            SocialProvider provider = SocialProvider.fromRegistrationId(registrationId)
                    .orElseThrow(() -> new SocialLoginException(
                            "UNSUPPORTED_SOCIAL_PROVIDER",
                            "Unsupported social provider: " + registrationId
                    ));

            SocialProfile profile = extractProfile(provider, attributes);
            if (!StringUtils.hasText(profile.providerUserId()) || !StringUtils.hasText(profile.email())) {
                return fail(provider, "PROVIDER_PROFILE_INVALID", "Provider profile is missing required fields", null, null, ip, ua);
            }
            if (!profile.emailVerified()) {
                return fail(provider, "PROVIDER_EMAIL_NOT_VERIFIED", "Provider email is not verified", null, profile.email(), ip, ua);
            }

            return Blocking.call(() -> socialAccountRepository.findByProviderAndProviderUserIdWithUser(
                            provider,
                            profile.providerUserId()
                    ))
                    .flatMap(found -> found
                            .map(account -> loginExistingAccount(account, profile, ip, ua))
                            .orElseGet(() -> createOrRejectNewAccount(profile, ip, ua)));
        });
    }

    private Mono<SocialLoginResult> loginExistingAccount(SocialAccount socialAccount,
                                                         SocialProfile profile,
                                                         String ip,
                                                         String ua) {
        User user = socialAccount.getUser();
        if (user == null || user.getUserId() == null) {
            return fail(profile.provider(), "SOCIAL_ACCOUNT_INVALID", "Linked account is invalid", null, profile.email(), ip, ua);
        }
        if (user.getStatus() == null || !user.getStatus().equalsIgnoreCase("active")) {
            return fail(profile.provider(), "ACCOUNT_INACTIVE", "Account is inactive", user.getUserId(), profile.email(), ip, ua);
        }

        boolean dirty = false;
        if (!equalsNullable(socialAccount.getProviderEmail(), profile.email())) {
            socialAccount.setProviderEmail(profile.email());
            dirty = true;
        }
        if (StringUtils.hasText(profile.name()) && !equalsNullable(socialAccount.getProviderName(), profile.name())) {
            socialAccount.setProviderName(profile.name());
            dirty = true;
        }

        Mono<Void> sync = dirty
                ? Blocking.call(() -> socialAccountRepository.save(socialAccount)).then()
                : Mono.empty();

        return sync.then(issueSession(user, profile.provider(), profile.email(), ip, ua));
    }

    private Mono<SocialLoginResult> createOrRejectNewAccount(SocialProfile profile,
                                                             String ip,
                                                             String ua) {
        return Blocking.call(() -> userRepository.findByEmail(profile.email()))
                .flatMap(existing -> {
                    if (existing.isPresent()) {
                        User user = existing.get();
                        return fail(
                                profile.provider(),
                                "ACCOUNT_LINK_REQUIRED",
                                "Existing account requires explicit link",
                                user.getUserId(),
                                profile.email(),
                                ip,
                                ua
                        );
                    }

                    return Blocking.call(() -> createSocialAccountAtomically(profile))
                            .flatMap(savedUser -> authLoginLogService.event(
                                            signupEvent(profile.provider()),
                                            true,
                                            savedUser.getUserId(),
                                            savedUser.getEmail(),
                                            ip,
                                            ua,
                                            null,
                                            null
                                    ).onErrorResume(e -> Mono.empty())
                                    .then(issueSession(savedUser, profile.provider(), profile.email(), ip, ua)));
                });
    }

    private User createSocialAccountAtomically(SocialProfile profile) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        return tx.execute(status -> {
            User user = new User();
            user.setEmail(profile.email());
            user.setPasswordHash(null);
            user.setName(profile.name());
            user.setRole(UserRole.user);
            user.setStatus("active");
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(Instant.now());
            user.setGlossaryHover(false);
            user.setCreditBalance(SOCIAL_SIGNUP_INITIAL_CREDIT_BALANCE);

            User savedUser = userRepository.saveAndFlush(user);

            SocialAccount socialAccount = new SocialAccount();
            socialAccount.setUser(savedUser);
            socialAccount.setProvider(profile.provider());
            socialAccount.setProviderUserId(profile.providerUserId());
            socialAccount.setProviderEmail(profile.email());
            socialAccount.setProviderName(profile.name());
            socialAccount.setLinkedAt(Instant.now());
            socialAccountRepository.saveAndFlush(socialAccount);

            return savedUser;
        });
    }

    private Mono<SocialLoginResult> issueSession(User user,
                                                 SocialProvider provider,
                                                 String email,
                                                 String ip,
                                                 String ua) {
        return loginSessionService.issueRefreshToken(user, ip, ua, null)
                .flatMap(refreshToken -> authLoginLogService.event(
                                loginEvent(provider),
                                true,
                                user.getUserId(),
                                email,
                                ip,
                                ua,
                                null,
                                null
                        ).onErrorResume(e -> Mono.empty())
                        .thenReturn(new SocialLoginResult(refreshToken)));
    }

    private Mono<SocialLoginResult> fail(SocialProvider provider,
                                         String code,
                                         String message,
                                         Long userId,
                                         String email,
                                         String ip,
                                         String ua) {
        return authLoginLogService.event(loginEvent(provider), false, userId, email, ip, ua, code, message)
                .onErrorResume(e -> Mono.empty())
                .then(Mono.error(new SocialLoginException(code, message)));
    }

    private SocialProfile extractProfile(SocialProvider provider, Map<String, Object> attributes) {
        return switch (provider) {
            case GOOGLE -> extractGoogleProfile(attributes);
            case KAKAO -> extractKakaoProfile(attributes);
            case NAVER -> extractNaverProfile(attributes);
        };
    }

    private SocialProfile extractGoogleProfile(Map<String, Object> attributes) {
        return new SocialProfile(
                SocialProvider.GOOGLE,
                getString(attributes, "sub"),
                getString(attributes, "email"),
                getString(attributes, "name"),
                getBoolean(attributes, "email_verified")
        );
    }

    private SocialProfile extractKakaoProfile(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = getMap(attributes, "kakao_account");
        Map<String, Object> profile = getMap(kakaoAccount, "profile");
        Map<String, Object> properties = getMap(attributes, "properties");

        String providerUserId = getString(attributes, "id");
        String email = getString(kakaoAccount, "email");
        String name = firstNonBlank(
                getString(kakaoAccount, "name"),
                getString(profile, "nickname"),
                getString(properties, "nickname")
        );
        boolean hasEmail = getBoolean(kakaoAccount, "has_email") || StringUtils.hasText(email);
        boolean emailNeedsAgreement = getBoolean(kakaoAccount, "email_needs_agreement");
        boolean emailVerified = getBoolean(kakaoAccount, "is_email_verified");

        if (!hasEmail || emailNeedsAgreement) {
            email = null;
        }

        return new SocialProfile(
                SocialProvider.KAKAO,
                providerUserId,
                email,
                name,
                emailVerified
        );
    }

    private SocialProfile extractNaverProfile(Map<String, Object> attributes) {
        Map<String, Object> response = getMap(attributes, "response");

        return new SocialProfile(
                SocialProvider.NAVER,
                getString(response, "id"),
                getString(response, "email"),
                firstNonBlank(getString(response, "name"), getString(response, "nickname")),
                StringUtils.hasText(getString(response, "email"))
        );
    }

    private static String loginEvent(SocialProvider provider) {
        return "OAUTH2_LOGIN_" + provider.name();
    }

    private static String signupEvent(SocialProvider provider) {
        return "OAUTH2_SIGNUP_" + provider.name();
    }

    private static Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null) {
            return Map.of();
        }
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> normalized = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String stringKey) {
                    normalized.put(stringKey, entry.getValue());
                }
            }
            return normalized;
        }
        return Map.of();
    }

    private static String getString(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static boolean getBoolean(Map<String, Object> attributes, String key) {
        Object value = attributes.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }
}
