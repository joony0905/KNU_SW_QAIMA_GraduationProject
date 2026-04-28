package com.qaima.service.auth;

import com.qaima.domain.EmailVerification;
import com.qaima.domain.PwdReset;
import com.qaima.domain.User;
import com.qaima.repository.EmailVerificationRepository;
import com.qaima.repository.PwdResetRepository;
import com.qaima.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailAuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PwdResetRepository pwdResetRepository;

    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${qaima.app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${qaima.mail.from:noreply@qaima.local}")
    private String fromAddress;

    @Value("${qaima.mail.dry-run:false}")
    private boolean mailDryRun;

    private static final Duration VERIFY_TTL = Duration.ofMinutes(10);
    private static final Duration RESET_TTL  = Duration.ofMinutes(30);
    private static final Duration RATE_LIMIT = Duration.ofSeconds(60);

    @Transactional
    public void requestEmailVerificationCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user != null && user.isEmailVerified()) {
            return;
        }

        Instant now = Instant.now();
        if (emailVerificationRepository.existsByEmailAndCreatedAtAfter(normalizedEmail, now.minus(RATE_LIMIT))) {
            return;
        }

        emailVerificationRepository.deleteByEmail(normalizedEmail);

        String code = generate6DigitCode();
        String tokenHash = sha256Hex(code);

        EmailVerification token = new EmailVerification();
        token.setUser(user);
        token.setEmail(normalizedEmail);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(now.plus(VERIFY_TTL));
        emailVerificationRepository.save(token);

        String subject = "[QAIMA] 이메일 인증번호";
        String body = ""
                + "QAIMA 이메일 인증번호입니다.\n\n"
                + "인증번호: " + code + "\n\n"
                + "만료 시간: " + VERIFY_TTL.toMinutes() + "분\n"
                + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.\n";

        sendTextMail(normalizedEmail, subject, body);
    }

    @Transactional
    public void confirmEmailVerificationCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        if (user != null && user.isEmailVerified()) {
            return;
        }

        String tokenHash = sha256Hex(normalizeCode(code));
        EmailVerification token = emailVerificationRepository
                .findByEmailAndTokenHash(normalizedEmail, tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않습니다."));

        Instant now = Instant.now();
        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("이미 사용된 인증번호입니다.");
        }
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("만료된 인증번호입니다.");
        }

        if (user != null) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
            token.setUser(user);
        }
        token.setUsedAt(now);
    }

    @Transactional
    public void consumeSignupEmailVerification(String email) {
        String normalizedEmail = normalizeEmail(email);
        Instant now = Instant.now();
        emailVerificationRepository
                .findFirstByEmailAndUsedAtIsNotNullAndExpiresAtAfterOrderByUsedAtDescCreatedAtDesc(normalizedEmail, now)
                .orElseThrow(() -> new IllegalArgumentException("회원가입 전에 이메일 인증이 필요합니다."));

        emailVerificationRepository.deleteByEmail(normalizedEmail);
    }

    @Transactional
    public void requestPasswordResetLink(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email)).orElse(null);
        if (user == null) {
            return;
        }

        Instant now = Instant.now();
        if (pwdResetRepository.existsByUserAndCreatedAtAfter(user, now.minus(RATE_LIMIT))) {
            return;
        }

        pwdResetRepository.deleteByUser(user);

        String rawToken = generateUrlSafeToken();
        String tokenHash = sha256Hex(rawToken);

        PwdReset token = new PwdReset();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(now.plus(RESET_TTL));
        pwdResetRepository.save(token);

        String link = baseUrl + "/api/v1/email/pwdreset/form?token=" + rawToken;

        String subject = "[QAIMA] 비밀번호 재설정 링크";
        String body = ""
                + "QAIMA 비밀번호 재설정 링크입니다.\n\n"
                + link + "\n\n"
                + "만료 시간: " + RESET_TTL.toMinutes() + "분\n"
                + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.\n";

        sendTextMail(user.getEmail(), subject, body);
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("새 비밀번호는 필수입니다.");
        }
        if (newPassword.length() < 8) {
            throw new IllegalArgumentException("비밀번호는 8자 이상이어야 합니다.");
        }

        Instant now = Instant.now();
        String tokenHash = sha256Hex(rawToken);

        PwdReset token = pwdResetRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (token.getUsedAt() != null) {
            throw new IllegalArgumentException("이미 사용된 토큰입니다.");
        }
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("만료된 토큰입니다.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);
    }

    private void sendTextMail(String to, String subject, String text) {
        try {
            if (shouldSkipDelivery(to)) {
                log.info("[MailAuthService] skip mail delivery. to={}, subject={}", to, subject);
                return;
            }

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setFrom(fromAddress);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("메일 발송에 실패했습니다.");
        }
    }

    private boolean shouldSkipDelivery(String to) {
        if (mailDryRun) {
            return true;
        }
        if (to == null) {
            return false;
        }
        String normalized = to.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith("@example.com");
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("인증번호는 필수입니다.");
        }
        return code.trim();
    }

    private static String generate6DigitCode() {
        int n = 100000 + new SecureRandom().nextInt(900000);
        return Integer.toString(n);
    }

    private static String generateUrlSafeToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("해시 생성 실패");
        }
    }
}
