package com.qaima.service.portfolio;

import com.qaima.common.Blocking;
import com.qaima.domain.Portfolio;
import com.qaima.domain.PortfolioHolding;
import com.qaima.domain.User;
import com.qaima.dto.portfolio.PortfolioHoldingDto;
import com.qaima.dto.portfolio.PortfolioResponseDto;
import com.qaima.dto.portfolio.PortfolioSaveRequestDto;
import com.qaima.repository.PortfolioRepository;
import com.qaima.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public Mono<PortfolioResponseDto> getMyPortfolio(Long userId) {
        return Blocking.call(() -> portfolioRepository.findByUserIdWithHoldings(userId)
                .map(PortfolioResponseDto::from)
                .orElseGet(PortfolioResponseDto::empty));
    }

    public Mono<PortfolioResponseDto> replaceMyPortfolio(Long userId, PortfolioSaveRequestDto request) {
        return Blocking.call(() -> transactionTemplate.execute(status -> replaceMyPortfolioBlocking(userId, request)));
    }

    private PortfolioResponseDto replaceMyPortfolioBlocking(Long userId, PortfolioSaveRequestDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Portfolio portfolio = portfolioRepository.findByUserIdWithHoldings(userId)
                .orElseGet(() -> {
                    Portfolio created = new Portfolio();
                    created.setUser(user);
                    return created;
                });

        portfolio.setCashAmount(request.cashAmount() == null ? BigDecimal.ZERO : request.cashAmount());
        portfolio.replaceHoldings(toEntities(request.holdings()));

        return PortfolioResponseDto.from(portfolioRepository.save(portfolio));
    }

    private List<PortfolioHolding> toEntities(List<PortfolioHoldingDto> holdings) {
        if (holdings == null || holdings.isEmpty()) {
            return List.of();
        }

        List<PortfolioHolding> entities = new ArrayList<>();
        for (int i = 0; i < holdings.size(); i++) {
            PortfolioHoldingDto dto = holdings.get(i);
            PortfolioHolding holding = new PortfolioHolding();
            holding.setStockCode(dto.stockCode().trim());
            holding.setStockName(dto.stockName().trim());
            holding.setQuantity(dto.quantity());
            holding.setAveragePrice(dto.averagePrice());
            holding.setPosition(i);
            entities.add(holding);
        }
        return entities;
    }
}
