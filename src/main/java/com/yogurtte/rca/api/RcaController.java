package com.yogurtte.rca.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.service.RcaService;

@RestController
@Validated
public class RcaController {

    private final RcaService rcaService;

    public RcaController(RcaService rcaService) {
        this.rcaService = rcaService;
    }

    /** mode: rca(기본, 장애 원인 분석) | review(정상 트레이스 성능 리뷰). */
    public record InvestigateRequest(
            @NotBlank String traceId,
            String question,
            @Pattern(regexp = "rca|review", message = "must be one of: rca, review") String mode) {
    }

    @PostMapping("/investigate")
    public ResponseEntity<RcaReport> investigate(@RequestBody @Valid InvestigateRequest request) {
        return ResponseEntity.ok(rcaService.investigate(request.traceId(), request.question(), request.mode()));
    }
}
