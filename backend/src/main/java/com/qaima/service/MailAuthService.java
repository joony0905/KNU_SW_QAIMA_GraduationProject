package com.qaima.service;

import com.qaima.domain.EmailVerification;
import com.qaima.domain.PwdReset;
import com.qaima.domain.User;
import com.qaima.repository.EmailVerificationRepository;
import com.qaima.repository.PwdResetRepository;
import com.qaima.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class MailAuthService {

    private final UserRepository userRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PwdResetRepository PwdResetRepository;

    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    @Value("${qaima.app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${qaima.mail.from:noreply@qaima.local}")
    private String fromAddress;

    // 이메일 인증 코드는 짧게(보통 5~15분)
    private static final Duration VERIFY_TTL = Duration.ofMinutes(10);
    private static final Duration RESET_TTL  = Duration.ofMinutes(30);
    private static final Duration RATE_LIMIT = Duration.ofSeconds(60);

    /**
     * 회원가입 이메일 인증번호 발송 (6자리)
     * - 메일에는 "인증번호"만 포함 (링크 X)
     */
    @Transactional
    public void requestEmailVerificationCode(String email) {
        User user = userRepository.findByEmail(email).orElse(null);

        // 존재 여부 노출 방지: 없으면 성공처럼 종료
        if (user == null) return;
        if (user.isEmailVerified()) return;

        Instant now = Instant.now();
        if (emailVerificationRepository.existsByUserAndCreatedAtAfter(user, now.minus(RATE_LIMIT))) {
            return;
        }

        // 재발송 시 기존 토큰 폐기
        emailVerificationRepository.deleteByUser(user);

        String code = generate6DigitCode();
        String tokenHash = sha256Hex(code);

        EmailVerification token = new EmailVerification();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(now.plus(VERIFY_TTL));
        emailVerificationRepository.save(token);

        String subject = "[QAIMA] 회원가입 이메일 인증번호";
        String body = ""
                + "QAIMA 회원가입 이메일 인증번호입니다.\n\n"
                + "인증번호: " + code + "\n\n"
                + "만료 시간: " + VERIFY_TTL.toMinutes() + "분\n"
                + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.\n";

        sendTextMail(user.getEmail(), subject, body);
    }


    /**
     * 최초 인증번호 발송
     */

    @Transactional
    public void confirmEmailVerificationCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 요청입니다."));

        if (user.isEmailVerified()) return;

        String tokenHash = sha256Hex(code);
        EmailVerification token = emailVerificationRepository
                .findByUserAndTokenHash(user, tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("인증번호가 올바르지 않습니다."));

        Instant now = Instant.now();
        if (token.getUsedAt() != null) throw new IllegalArgumentException("이미 사용된 인증번호입니다.");
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("만료된 인증번호입니다.");
        }

        user.setEmailVerified(true);
        user.setEmailVerifiedAt(now);
        token.setUsedAt(now);

        emailVerificationRepository.deleteByUser(user);
    }

    /**
     * 비밀번호 재설정 링크 발송
     */
    @Transactional
    public void requestPasswordResetLink(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return;

        Instant now = Instant.now();
        if (PwdResetRepository.existsByUserAndCreatedAtAfter(user, now.minus(RATE_LIMIT))) {
            return;
        }

        PwdResetRepository.deleteByUser(user);

        String rawToken = generateUrlSafeToken();
        String tokenHash = sha256Hex(rawToken);

        PwdReset token = new PwdReset();
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(now.plus(RESET_TTL));
        PwdResetRepository.save(token);

        String link = baseUrl + "/api/v1/email/pwdreset/form?token=" + rawToken;

        String subject = "[QAIMA] 비밀번호 재설정 링크";
        String body = ""
                + "QAIMA 비밀번호 재설정 링크입니다.\n\n"
                + link + "\n\n"
                + "만료 시간: " + RESET_TTL.toMinutes() + "분\n"
                + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.\n";

        sendTextMail(user.getEmail(), subject, body);
    }

    /**
     * 비밀번호 재설정
     */

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

        PwdReset token = PwdResetRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));

        if (token.getUsedAt() != null) throw new IllegalArgumentException("이미 사용된 토큰입니다.");
        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("만료된 토큰입니다.");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(now);

        // 강추: Refresh Token/세션이 있으면 여기서 전부 폐기
    }


    private void sendTextMail(String to, String subject, String text) {
        try {
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

    private static String generate6DigitCode() {
        // 000000~999999 방지 위해 100000~999999
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
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("해시 생성 실패");
        }
    }
}
