package com.qaima.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
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
        name = "sec_13f_filing",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sec_13f_filing_accession", columnNames = "accession_number")
        },
        indexes = {
                @Index(name = "idx_sec_13f_filing_manager_period", columnList = "manager_cik, report_period"),
                @Index(name = "idx_sec_13f_filing_report_period", columnList = "report_period"),
                @Index(name = "idx_sec_13f_filing_source_file", columnList = "source_file")
        }
)
public class Sec13fFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sec_13f_filing_id")
    private Long sec13fFilingId;

    @Column(name = "accession_number", nullable = false, length = 32)
    private String accessionNumber;

    @Column(name = "manager_cik", nullable = false, length = 10)
    private String managerCik;

    @Column(name = "manager_name", length = 255)
    private String managerName;

    @Column(name = "filing_date", nullable = false)
    private LocalDate filingDate;

    @Column(name = "report_period", nullable = false)
    private LocalDate reportPeriod;

    @Column(name = "submission_type", nullable = false, length = 20)
    private String submissionType;

    @Column(name = "is_amendment", nullable = false)
    private Boolean amendment = false;

    @Column(name = "amendment_no", length = 16)
    private String amendmentNo;

    @Column(name = "amendment_type", length = 50)
    private String amendmentType;

    @Column(name = "source_file", nullable = false, length = 255)
    private String sourceFile;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    private void applyDefaults() {
        if (amendment == null) {
            amendment = false;
        }
    }
}
