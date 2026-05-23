package com.qaima.service.batch;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "kis-batch")
public class KisBatchProperties {

    private RateLimit rateLimit = new RateLimit();
    private IndustryIndexOhlcv industryIndexOhlcv = new IndustryIndexOhlcv();
    private MarketInvestorFlow marketInvestorFlow = new MarketInvestorFlow();
    private StockInvestorFlow stockInvestorFlow = new StockInvestorFlow();

    @Getter
    @Setter
    public static class RateLimit {
        private int maxRequestsPerSecond = 10;
    }

    @Getter
    @Setter
    public static class Retry {
        private int maxAttempts = 2;
        private long delayMs = 2000;
    }

    @Getter
    @Setter
    public static class IndustryIndexOhlcv {
        private boolean enabled = true;
        private String dailyCron = "0 10 18 * * MON-FRI";
        private int lookbackDays = 1095;
        private int refreshTailDays = 10;
        private int limit = 0;
    }

    @Getter
    @Setter
    public static class MarketInvestorFlow {
        private boolean enabled = true;
        private String dailyCron = "0 30 16 * * MON-FRI";
        private int lookbackDays = 30;
        private int refreshTailDays = 10;
        private Retry retry = new Retry();
    }

    @Getter
    @Setter
    public static class StockInvestorFlow {
        private boolean enabled = true;
        private String dailyCron = "0 0 19 * * MON-FRI";
        private int lookbackDays = 30;
        private int refreshTailDays = 10;
        private int limit = 300;
        private long sleepMs = 300;
        private Retry retry = new Retry();
    }
}
