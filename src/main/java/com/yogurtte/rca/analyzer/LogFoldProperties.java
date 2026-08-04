package com.yogurtte.rca.analyzer;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 로그 접기(B-34) 설정. <b>대조군 스위치를 남기는 것이 이 record의 존재 이유다</b> —
 * 접기가 원인 판정을 바꾸지 않는다는 것은 {@code enabled=false} 팔과 대조해야만 말할 수 있고,
 * 하위 스위치가 갈려 있어야 <b>세 규칙 중 어느 것이 효과를 냈는지</b>도 갈린다.
 *
 * @param enabled        접기 사용 여부. {@code false}면 어셈블 결과가 <b>바이트 단위로</b> 접기
 *                       이전과 같다 — 접을 것이 없으면 원본 문자열을 그대로 돌려주기 때문이다.
 * @param appPackages    앱 프레임으로 볼 패키지 접두. <b>접두 목록만으로는 부족하다</b> —
 *                       리포트 44건 인용 집계에서 공동 2위가 {@code RedisLockProvider}(5회)인데
 *                       {@code net.javacrumbs.shedlock…} 서드파티다. 앱이 직접 호출하는 경계라
 *                       실질 앱 프레임인데 접두 규칙만 쓰면 잘려나간다. 그래서
 *                       {@link LogStackFold}가 <b>앱 프레임 경계 ±1 프레임</b>을 함께 보존한다.
 * @param minRun         접을 최소 연속 라이브러리 프레임 수. 1줄은 접어도 표식 줄로 바뀔 뿐이라
 *                       이득 없이 문맥만 잃는다.
 * @param stripLineNoise 줄 안의 잡음 제거. <b>정보가 0인 것만</b> 뺀다 — ANSI 색코드(실측 4.0%),
 *                       한 줄에 두 번 찍히는 traceId-spanId(2.4%), 스트림 라벨과 같은 값인
 *                       {@code --- [service]}(1.1%), 로거명 정렬 공백(0.3%). 스레드명·로거명·
 *                       메시지·<b>숫자</b>는 건드리지 않는다.
 * @param nearRepeatMin  숫자만 다른 근사 반복을 접기 시작할 벌 수 (0 이하 = 끄기). 이 접기는
 *                       <b>첫 벌과 끝 벌을 원문 그대로 싣고 사이만</b> 접는다 — 수치가 근거인
 *                       문항({@code Remaining time: 29999 ms})에서 값의 양 끝을 잃지 않기 위해서다.
 */
@ConfigurationProperties("rca.collect.log-fold")
public record LogFoldProperties(
        boolean enabled,
        List<String> appPackages,
        int minRun,
        boolean stripLineNoise,
        int nearRepeatMin) {

    public LogFoldProperties {
        appPackages = (appPackages == null || appPackages.isEmpty()) ? List.of("com.example") : appPackages;
        minRun = minRun <= 0 ? 2 : minRun;
    }

    /** 프레임 접기만 켠 구성 — 테스트·대조군용. */
    public static LogFoldProperties framesOnly(List<String> appPackages) {
        return new LogFoldProperties(true, appPackages, 2, false, 0);
    }

    public static LogFoldProperties off() {
        return new LogFoldProperties(false, null, 2, false, 0);
    }
}
