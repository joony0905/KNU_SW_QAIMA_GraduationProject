package com.qaima.repository;

import com.qaima.domain.AnalysisReport;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    Page<AnalysisReport> findByUser_UserIdOrderByGeneratedAtDesc(Long userId, Pageable pageable);
    Page<AnalysisReport> findByUser_UserIdAndFeatureTypeOrderByGeneratedAtDesc(Long userId, String featureType, Pageable pageable);
    Optional<AnalysisReport> findByReportIdAndUser_UserId(Long reportId, Long userId);
    Page<AnalysisReport> findByUser_UserIdAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(Long userId, Instant generatedAt, Pageable pageable);
    Page<AnalysisReport> findByUser_UserIdAndFeatureTypeAndGeneratedAtGreaterThanEqualOrderByGeneratedAtDesc(Long userId, String featureType, Instant generatedAt, Pageable pageable);
    Optional<AnalysisReport> findByReportIdAndUser_UserIdAndGeneratedAtGreaterThanEqual(Long reportId, Long userId, Instant generatedAt);

    @Transactional
    long deleteByUser_UserIdAndGeneratedAtBefore(Long userId, Instant generatedAt);

    @Transactional
    long deleteByGeneratedAtBefore(Instant generatedAt);
}
