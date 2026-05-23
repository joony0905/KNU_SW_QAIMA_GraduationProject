package com.qaima.repository;

import com.qaima.domain.AnalysisReport;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {
    Page<AnalysisReport> findByUser_UserIdOrderByGeneratedAtDesc(Long userId, Pageable pageable);
    Page<AnalysisReport> findByUser_UserIdAndFeatureTypeOrderByGeneratedAtDesc(Long userId, String featureType, Pageable pageable);
    Optional<AnalysisReport> findByReportIdAndUser_UserId(Long reportId, Long userId);
}
