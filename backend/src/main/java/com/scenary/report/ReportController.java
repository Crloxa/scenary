package com.scenary.report;

import com.scenary.auth.UserContext;
import com.scenary.common.Result;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 举报接口（docs/02 §10.1，P18）。路径 /api/v1/reports 由 AuthInterceptor 保护（登录举报）。
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/reports")
    public Result<ReportCreatedVO> create(@Valid @RequestBody ReportRequest req) {
        return Result.ok(reportService.create(UserContext.require(), req));
    }
}
