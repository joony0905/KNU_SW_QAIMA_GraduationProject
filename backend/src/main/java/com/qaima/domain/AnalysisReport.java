package com.qaima.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "analysis_report",
        indexes = {
                @Index(name = "idx_analysis_report_user_generated", columnList = "user_id, generated_at"),
                @Index(name = "idx_analysis_report_feature", columnList = "feature_type, generated_at"),
                @Index(name = "idx_analysis_report_stock", columnList = "stock_code, generated_at")
        }
)
public class AnalysisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "feature_type", nullable = false, length = 20)
    private String featureType;

    @Column(name = "subject_type", nullable = false, length = 20)
    private String subjectType;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;

    @Column(name = "stock_code", length = 32)
    private String stockCode;

    @Column(name = "company_name", length = 255)
    private String companyName;

    @Column(name = "portfolio_summary", length = 500)
    private String portfolioSummary;

    @Column(name = "analysis_model", length = 100)
    private String analysisModel;

    @Column(name = "invest_level", length = 30)
    private String investLevel;

    @Column(name = "risk_profile", length = 50)
    private String riskProfile;

    @Column(name = "analysis_window", length = 50)
    private String analysisWindow;

    @Column(name = "price_basis", length = 50)
    private String priceBasis;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "data_as_of", length = 100)
    private String dataAsOf;

    @Column(name = "request_payload_json", nullable = false, columnDefinition = "json")
    private String requestPayloadJson;

    @Column(name = "result_snapshot_json", nullable = false, columnDefinition = "json")
    private String resultSnapshotJson;

    @Column(name = "warnings_json", columnDefinition = "json")
    private String warningsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
