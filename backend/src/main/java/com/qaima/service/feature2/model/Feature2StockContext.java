package com.qaima.service.feature2.model;

import com.qaima.domain.Stock;
import com.qaima.dto.stock.StockMeta;

public record Feature2StockContext(
        Stock stock,
        StockMeta stockMeta
) {
}
