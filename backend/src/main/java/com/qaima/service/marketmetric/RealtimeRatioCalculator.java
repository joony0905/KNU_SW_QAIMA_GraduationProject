package com.qaima.service.marketmetric;

import com.qaima.service.marketmetric.model.RealtimeRatioView;
import com.qaima.service.marketmetric.model.SnapshotMetricView;
import com.qaima.service.marketmetric.support.MetricMath;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class RealtimeRatioCalculator {

    public RealtimeRatioView calculate(BigDecimal price, SnapshotMetricView snapshot) {
        if (snapshot == null) {
            return new RealtimeRatioView(price, null, null, null, null, null);
        }

        return new RealtimeRatioView(
                price,
                multiply(price, snapshot.sharesOutstanding()),
                multiply(price, snapshot.floatingShares()),
                MetricMath.toDouble(MetricMath.divide(price, snapshot.epsTtm())),
                MetricMath.toDouble(MetricMath.divide(price, snapshot.bps())),
                MetricMath.toDouble(MetricMath.divide(price, snapshot.sps()))
        );
    }

    private BigDecimal multiply(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return null;
        }
        return left.multiply(right);
    }
}
