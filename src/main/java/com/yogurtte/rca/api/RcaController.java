package com.yogurtte.rca.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.yogurtte.rca.report.RcaReport;
import com.yogurtte.rca.service.RcaService;
import com.yogurtte.rca.triage.TriageService;

@RestController
@Validated
public class RcaController {

    private final RcaService rcaService;
    private final TriageService triageService;

    public RcaController(RcaService rcaService, TriageService triageService) {
        this.rcaService = rcaService;
        this.triageService = triageService;
    }

    /** mode: rca(기본, 장애 원인 분석) | review(정상 트레이스 성능 리뷰). */
    public record InvestigateRequest(
            @NotBlank String traceId,
            String question,
            @Pattern(regexp = "rca|review", message = "must be one of: rca, review") String mode) {
    }

    /**
     * 조사할 대상을 이미 아는 경우. 탐색 단계를 건너뛴다.
     *
     * <p>탐색이 붙은 뒤에도 이 진입점은 그대로 남는다 — 분석 능력만 따로 재려면 대상이 고정된
     * 입력이 필요하고, 과거 회차와의 비교도 이 경로로만 성립한다.
     */
    @PostMapping("/investigate")
    public ResponseEntity<RcaReport> investigate(@RequestBody @Valid InvestigateRequest request) {
        return ResponseEntity.ok(rcaService.investigate(request.traceId(), request.question(), request.mode()));
    }

    /**
     * 자연어 질문만으로 시작하는 조사 — "어젯밤에 댓글 알림이 안 왔어요".
     *
     * @param from 시간창을 직접 지정할 때(UTC ISO-8601). 주면 질문의 시간 표현보다 우선한다.
     */
    public record DiagnoseRequest(
            @NotBlank String question,
            Instant from,
            Instant to,
            @Pattern(regexp = "rca|review", message = "must be one of: rca, review") String mode) {
    }

    @PostMapping("/diagnose")
    public ResponseEntity<RcaReport> diagnose(@RequestBody @Valid DiagnoseRequest request) {
        return ResponseEntity.ok(triageService.diagnose(
                request.question(), request.from(), request.to(), request.mode()));
    }
}
