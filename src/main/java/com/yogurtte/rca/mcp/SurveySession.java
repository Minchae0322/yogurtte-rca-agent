package com.yogurtte.rca.mcp;

import java.time.Instant;
import java.util.List;

import com.yogurtte.rca.collector.TimeWindow;
import com.yogurtte.rca.report.Evidence;
import com.yogurtte.rca.triage.incident.Incident;

/**
 * 탐색 한 번의 결과. MCP는 툴 호출이 서로 독립이라 <b>후보 목록을 들고 있을 자리가 필요하다</b>.
 *
 * <p><b>왜 핸들을 돌려주고 받는가.</b> 모델이 다음 호출에서 넘기는 것은 <b>후보 번호뿐</b>이어야
 * 한다(전체구조.md §9 - 조사 창·서비스·traceId는 코드가 고른 후보에서 파생한다). 모델이 창이나
 * traceId를 직접 써 보낼 수 있게 하면 그 경계가 무너지고, 회차마다 다른 창이 나와 재현이 깨진다.
 * 그래서 창·서비스·traceId는 전부 이 세션 안에 남겨 두고 밖으로 내보내지 않는다.
 *
 * @param id         탐색 핸들. {@code TriageService}와 같은 규칙({@code scan-<epoch>})이라
 *                   리포트·로그에서 같은 조사를 추적할 수 있다
 * @param question   그때 받은 질문 문안. 분석 컨텍스트 조립에 다시 쓴다 (문안이 바뀌면 다른 조사다)
 * @param sweepWindow 스윕 창. 후보 창을 여기로 clamp 한다
 * @param incidents  코드가 묶은 후보 전부. 모델이 고른 것만 쓰되 목록은 남긴다
 * @param traceHits  스윕이 찾은 이상 트레이스. 신뢰 가능한 것만 수집 후보로 넘어간다
 * @param createdAt  생성 시각. 오래된 세션을 버리는 기준
 */
record SurveySession(
        String id,
        String question,
        TimeWindow sweepWindow,
        List<Incident> incidents,
        List<Evidence.TraceHit> traceHits,
        Instant createdAt) {

    SurveySession {
        incidents = incidents == null ? List.of() : List.copyOf(incidents);
        traceHits = traceHits == null ? List.of() : List.copyOf(traceHits);
    }

    /** 모델이 준 번호로 후보를 찾는다. 없는 번호는 조용히 버리지 않고 호출자가 알려 준다. */
    List<Incident> chosen(List<String> incidentIds) {
        return incidents.stream().filter(i -> incidentIds.contains(i.id())).toList();
    }

    List<String> knownIds() {
        return incidents.stream().map(Incident::id).toList();
    }
}
