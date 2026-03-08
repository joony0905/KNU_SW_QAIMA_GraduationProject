package com.qaima.api.MetaController;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.stock.StockMeta;
import com.qaima.external.StockClient;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/meta")
@RequiredArgsConstructor
public class MetaController {

    private final StockClient stockClient;

    @GetMapping("/tickers")
    public Mono<ApiResponse<StockMeta>> getTickerMeta(
            @RequestParam String symbol
    ) {
        return stockClient.fetchTickerMeta(symbol)
                .flatMap(response -> {
                    if (response != null && response.isSuccess()) {
                        return Mono.just(response);
                    }

                    String errorCode = "META_NOT_FOUND";
                    String message = "티커 메타 정보를 가져오지 못했습니다.";

                    if (response != null && response.getErrors() != null && !response.getErrors().isEmpty()) {
                        var first = response.getErrors().get(0);
                        if (first.getCode() != null && !first.getCode().isBlank()) {
                            errorCode = first.getCode();
                        }
                        if (first.getMessage() != null && !first.getMessage().isBlank()) {
                            message = first.getMessage();
                        }
                    }

                    ErrorCode mapped = "META_NOT_FOUND".equalsIgnoreCase(errorCode)
                            ? ErrorCode.RESOURCE_NOT_FOUND
                            : ErrorCode.INTERNAL_ERROR;

                    return Mono.error(new ErrorException(mapped, message));
                });
    }
}
