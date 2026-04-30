package com.qaima.service.credit;

import com.qaima.common.Blocking;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.CreditLedgerType;
import com.qaima.domain.CreditReferenceType;
import com.qaima.domain.User;
import com.qaima.domain.UserCreditLedger;
import com.qaima.dto.credit.CreditBalanceDto;
import com.qaima.dto.credit.CreditLedgerDto;
import com.qaima.repository.UserCreditLedgerRepository;
import com.qaima.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CreditService {

    public static final long FEATURE1_ANALYSIS_COST = 1L;
    public static final long FEATURE2_ANALYSIS_COST = 1L;

    private final UserRepository userRepository;
    private final UserCreditLedgerRepository ledgerRepository;
    private final TransactionTemplate transactionTemplate;

    public Mono<CreditBalanceDto> getBalance(Long userId) {
        return Blocking.call(() -> transactionTemplate.execute(status -> {
            User user = requireUser(userId);
            return new CreditBalanceDto(safeBalance(user));
        }));
    }

    public Mono<List<CreditLedgerDto>> getLedger(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return Blocking.call(() -> transactionTemplate.execute(status -> {
            requireUser(userId);
            return ledgerRepository
                    .findByUserUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, safeLimit))
                    .stream()
                    .map(this::toDto)
                    .toList();
        }));
    }

    public Mono<CreditLedgerDto> useFeature1(Long userId, String referenceId) {
        return use(userId, FEATURE1_ANALYSIS_COST, CreditReferenceType.FEATURE1, referenceId, "FEATURE1_ANALYSIS");
    }

    public Mono<CreditLedgerDto> useFeature2(Long userId, String referenceId) {
        return use(userId, FEATURE2_ANALYSIS_COST, CreditReferenceType.FEATURE2, referenceId, "FEATURE2_ANALYSIS");
    }

    public Mono<CreditLedgerDto> refundFeature1(Long userId, String referenceId, String reason) {
        return refund(userId, FEATURE1_ANALYSIS_COST, CreditReferenceType.FEATURE1, referenceId, reason);
    }

    public Mono<CreditLedgerDto> refundFeature2(Long userId, String referenceId, String reason) {
        return refund(userId, FEATURE2_ANALYSIS_COST, CreditReferenceType.FEATURE2, referenceId, reason);
    }

    public Mono<CreditLedgerDto> adjust(Long userId, Long amount, String reason) {
        return Blocking.call(() -> transactionTemplate.execute(status ->
                applyDelta(
                        userId,
                        requireNonZero(amount),
                        CreditLedgerType.ADJUST,
                        CreditReferenceType.ADMIN,
                        null,
                        normalizeReason(reason, "ADMIN_ADJUST")
                )
        ));
    }

    public Mono<CreditLedgerDto> charge(Long userId, Long amount, String reason) {
        return Blocking.call(() -> transactionTemplate.execute(status ->
                applyDelta(
                        userId,
                        requirePositive(amount),
                        CreditLedgerType.CHARGE,
                        CreditReferenceType.ADMIN,
                        null,
                        normalizeReason(reason, "ADMIN_CHARGE")
                )
        ));
    }

    private Mono<CreditLedgerDto> use(
            Long userId,
            long cost,
            CreditReferenceType referenceType,
            String referenceId,
            String reason
    ) {
        return Blocking.call(() -> transactionTemplate.execute(status -> {
            User user = requireUserForUpdate(userId);
            long current = safeBalance(user);
            if (current < cost) {
                throw new ErrorException(
                        ErrorCode.INSUFFICIENT_CREDIT,
                        "Insufficient credit balance."
                );
            }
            return applyDelta(user, -cost, CreditLedgerType.USE, referenceType, referenceId, reason);
        }));
    }

    private Mono<CreditLedgerDto> refund(
            Long userId,
            long amount,
            CreditReferenceType referenceType,
            String referenceId,
            String reason
    ) {
        return Blocking.call(() -> transactionTemplate.execute(status ->
                applyDelta(
                        userId,
                        amount,
                        CreditLedgerType.REFUND,
                        referenceType,
                        referenceId,
                        normalizeReason(reason, "ANALYSIS_REFUND")
                )
        ));
    }

    private CreditLedgerDto applyDelta(
            Long userId,
            long delta,
            CreditLedgerType type,
            CreditReferenceType referenceType,
            String referenceId,
            String reason
    ) {
        User user = requireUserForUpdate(userId);
        return applyDelta(user, delta, type, referenceType, referenceId, reason);
    }

    private CreditLedgerDto applyDelta(
            User user,
            long delta,
            CreditLedgerType type,
            CreditReferenceType referenceType,
            String referenceId,
            String reason
    ) {
        long nextBalance = safeBalance(user) + delta;
        if (nextBalance < 0) {
            throw new ErrorException(
                    ErrorCode.INSUFFICIENT_CREDIT,
                    "Insufficient credit balance."
            );
        }

        user.setCreditBalance(nextBalance);
        userRepository.save(user);

        UserCreditLedger ledger = new UserCreditLedger();
        ledger.setUser(user);
        ledger.setAmount(delta);
        ledger.setBalanceAfter(nextBalance);
        ledger.setType(type);
        ledger.setReason(reason);
        ledger.setReferenceType(referenceType);
        ledger.setReferenceId(referenceId);
        return toDto(ledgerRepository.saveAndFlush(ledger));
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
    }

    private User requireUserForUpdate(Long userId) {
        if (userId == null) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ErrorException(ErrorCode.RESOURCE_NOT_FOUND, "User not found."));
    }

    private long safeBalance(User user) {
        return user.getCreditBalance() == null ? 0L : user.getCreditBalance();
    }

    private long requirePositive(Long amount) {
        if (amount == null || amount <= 0) {
            throw new ErrorException(ErrorCode.VALIDATION_ERROR, "Credit amount must be positive.");
        }
        return amount;
    }

    private long requireNonZero(Long amount) {
        if (amount == null || amount == 0) {
            throw new ErrorException(ErrorCode.VALIDATION_ERROR, "Credit amount must not be zero.");
        }
        return amount;
    }

    private String normalizeReason(String reason, String fallback) {
        if (reason == null || reason.isBlank()) {
            return fallback;
        }
        return reason.length() > 100 ? reason.substring(0, 100) : reason;
    }

    private CreditLedgerDto toDto(UserCreditLedger ledger) {
        return new CreditLedgerDto(
                ledger.getLedgerId(),
                ledger.getAmount(),
                ledger.getBalanceAfter(),
                ledger.getType(),
                ledger.getReason(),
                ledger.getReferenceType(),
                ledger.getReferenceId(),
                ledger.getCreatedAt()
        );
    }
}
