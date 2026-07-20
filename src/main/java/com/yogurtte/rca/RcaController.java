package com.yogurtte.rca;

import jakarta.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.yogurtte.rca.report.RcaReport;

@RestController
@Validated
public class RcaController {

    private final RcaService rcaService;

    public RcaController(RcaService rcaService) {
        this.rcaService = rcaService;
    }

    public record InvestigateRequest(@NotBlank String traceId, String question) {
    }

    @PostMapping("/investigate")
    public ResponseEntity<RcaReport> investigate(@RequestBody @jakarta.validation.Valid InvestigateRequest request) {
        return ResponseEntity.ok(rcaService.investigate(request.traceId(), request.question()));
    }
}
