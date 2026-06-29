package com.qaima.api.report;

import com.qaima.common.ApiResponse;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.report.AnalysisReportDetailDto;
import com.qaima.dto.report.AnalysisReportSummaryDto;
import com.qaima.service.report.AnalysisReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Report")
@SecurityRequirement(name = "bearerAuth")
public class AnalysisReportController {

    private final AnalysisReportService reportService;

    @GetMapping("/me")
    @Operation(summary = "List my reports", description = "Returns paged analysis reports for the authenticated user.")
    public Mono<ApiResponse<List<AnalysisReportSummaryDto>>> myReports(
            Authentication authentication,
            @RequestParam(name = "featureType", required = false) String featureType,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return reportService.listMine(currentUserId(authentication), featureType, page, size)
                .map(ApiResponse::success);
    }

    @GetMapping("/{reportId}")
    @Operation(summary = "Get report detail", description = "Returns a single analysis report owned by the authenticated user.")
    public Mono<ApiResponse<AnalysisReportDetailDto>> detail(
            Authentication authentication,
            @PathVariable Long reportId
    ) {
        return reportService.getMine(currentUserId(authentication), reportId)
                .map(ApiResponse::success);
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new ErrorException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        }
        return userId;
    }
}
