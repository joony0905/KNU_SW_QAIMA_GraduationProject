package com.qaima.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "sec_13f_import_file",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sec_13f_import_file_source_file", columnNames = "source_file")
        },
        indexes = {
                @Index(name = "idx_sec_13f_import_file_status", columnList = "status, started_at")
        }
)
public class Sec13fImportFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sec_13f_import_file_id")
    private Long sec13fImportFileId;

    @Column(name = "source_file", nullable = false, length = 255)
    private String sourceFile;

    @Column(name = "source_path", length = 1000)
    private String sourcePath;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "submission_rows", nullable = false)
    private Long submissionRows = 0L;

    @Column(name = "cover_page_rows", nullable = false)
    private Long coverPageRows = 0L;

    @Column(name = "info_table_rows", nullable = false)
    private Long infoTableRows = 0L;

    @Column(name = "matched_info_table_rows", nullable = false)
    private Long matchedInfoTableRows = 0L;

    @Column(name = "skipped_unmapped_rows", nullable = false)
    private Long skippedUnmappedRows = 0L;

    @Column(name = "skipped_derivative_rows", nullable = false)
    private Long skippedDerivativeRows = 0L;

    @Column(name = "skipped_non_share_rows", nullable = false)
    private Long skippedNonShareRows = 0L;

    @Column(name = "parsed_holding_rows", nullable = false)
    private Long parsedHoldingRows = 0L;

    @Column(name = "created_filings", nullable = false)
    private Integer createdFilings = 0;

    @Column(name = "updated_filings", nullable = false)
    private Integer updatedFilings = 0;

    @Column(name = "created_holdings", nullable = false)
    private Integer createdHoldings = 0;

    @Column(name = "updated_holdings", nullable = false)
    private Integer updatedHoldings = 0;

    @Column(name = "unchanged_holdings", nullable = false)
    private Integer unchangedHoldings = 0;

    @Column(name = "aggregated_rows", nullable = false)
    private Integer aggregatedRows = 0;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void applyDefaults() {
        if (status == null || status.isBlank()) {
            status = "STARTED";
        }
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }
}
