package com.yogurtte.rca.analyzer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.yogurtte.rca.collector.TraceSpans;
import com.yogurtte.rca.report.ServiceGraph;

/**
 * 픽스처는 실제 조사에서 저장된 Tempo 원본이다(2026-07-31 검증에서 박제).
 * 여기 박힌 기대값은 그 검증의 실측 출력 그대로다 — 바뀌면 추출 규칙이 회귀한 것이다.
 */
class ServiceGraphExtractorTest {

    private final ServiceGraphExtractor extractor = new ServiceGraphExtractor();

    @Test
    void 같은_엣지의_span들이_한_줄로_접히고_에러와_이벤트가_실린다() {
        // 23 span짜리 트레이스: JDBC 세부 span 19개가 전부 같은 엣지다.
        var graph = extract("/traces/trace-jdbc-rollback.json");

        assertThat(graph.edges()).hasSize(1);
        var edge = graph.edges().get(0);
        assertThat(edge.kind()).isEqualTo("jdbc");
        assertThat(edge.source()).isEqualTo("content-service");
        assertThat(edge.target()).isEqualTo("mysql/content");
        assertThat(edge.detail()).isEqualTo("HikariPool-1");
        assertThat(edge.calls()).isEqualTo(19);
        // 접어도 정답 지문은 사라지면 안 된다 — 중복 키 에러와 롤백 이벤트가 엣지에 남는다.
        assertThat(edge.errors()).anySatisfy(e -> assertThat(e).contains("Duplicate entry"));
        assertThat(edge.events()).contains("rollback");
    }

    @Test
    void peer_service가_DB_이름이어도_자기_참조_엣지가_생기지_않는다() {
        // peer.service=content인 span이 트레이스당 수십 개다 — content-service가 아니라
        // MySQL 데이터베이스 이름이다. 표준 키를 먼저 보므로 전부 jdbc로 분류되어야 한다.
        for (var fixture : new String[] {"/traces/trace-jdbc-rollback.json",
                "/traces/trace-client-call-refused.json", "/traces/trace-kafka-publish-receive-dlq.json"}) {
            var graph = extract(fixture);
            assertThat(graph.edges())
                    .as(fixture)
                    .noneMatch(e -> e.source().equals(e.target()))
                    .noneMatch(e -> "unclassified".equals(e.kind()));
        }
    }

    @Test
    void 상대_서비스가_죽어_트레이스에_없어도_아웃바운드_호출이_엣지로_나온다() {
        // 66→28 span 전부가 content-service다 — auth-service는 다운이라 트레이스에 합류하지 못했다.
        // client.name 규칙(판별 ④)이 없으면 이 엣지는 조용히 사라진다(검증에서 실제로 사라졌다).
        var graph = extract("/traces/trace-client-call-refused.json");

        assertThat(graph.edges()).extracting(ServiceGraph.Edge::kind)
                .containsExactlyInAnyOrder("db", "jdbc", "service");

        var auth = edgeOfKind(graph, "service");
        assertThat(auth.source()).isEqualTo("content-service");
        assertThat(auth.target()).isEqualTo("auth-service");
        assertThat(auth.errors()).anySatisfy(e -> assertThat(e).contains("Connection refused"));

        var redis = edgeOfKind(graph, "db");
        assertThat(redis.target()).isEqualTo("redis");
        assertThat(redis.calls()).isEqualTo(4);
        assertThat(redis.operations()).contains("GET");
    }

    @Test
    void 카프카_엣지는_방향까지_나오고_receive_토픽명은_source_name에서_온다() {
        var graph = extract("/traces/trace-kafka-publish-receive-dlq.json");
        var messaging = graph.edges().stream().filter(e -> "messaging".equals(e.kind())).toList();

        // publish는 서비스 → 토픽, receive는 토픽 → 서비스.
        assertThat(messaging).anySatisfy(e -> {
            assertThat(e.source()).isEqualTo("content-service");
            assertThat(e.target()).isEqualTo("kafka/user.notifications");
            assertThat(e.operations()).contains("publish");
        });
        assertThat(messaging).anySatisfy(e -> {
            assertThat(e.source()).isEqualTo("kafka/user.notifications");
            assertThat(e.target()).isEqualTo("chat-service");
            assertThat(e.operations()).contains("receive");
            // MongoDB는 span을 만들지 않는다(실측 반증) — 장애는 receive span의 error 속성으로만 온다.
            assertThat(e.errors()).anySatisfy(err -> assertThat(err).contains("MongoSocketOpenException"));
        });
        assertThat(messaging).anySatisfy(e -> {
            assertThat(e.source()).isEqualTo("chat-service");
            assertThat(e.target()).isEqualTo("kafka/user.notifications.dlq");
        });
        // receive에서 destination만 보면 토픽이 "?"가 된다 — 그 회귀를 여기서 잡는다.
        assertThat(messaging).noneMatch(e -> e.source().contains("?") || e.target().contains("?"));
    }

    @Test
    void 관계_속성이_없는_트레이스는_빈_그래프다() {
        var graph = extractor.fromSpans(TraceSpans.parse("""
                {"batches":[{"resource":{"attributes":[
                    {"key":"service.name","value":{"stringValue":"chat"}}]},
                  "scopeSpans":[{"spans":[
                    {"name":"notify","spanId":"a","startTimeUnixNano":"1","endTimeUnixNano":"2"}]}]}]}
                """));
        assertThat(graph.isEmpty()).isTrue();
        assertThat(graph.toText()).contains("추출된 엣지 없음");
    }

    private static ServiceGraph.Edge edgeOfKind(ServiceGraph graph, String kind) {
        return graph.edges().stream().filter(e -> kind.equals(e.kind())).findFirst().orElseThrow();
    }

    private ServiceGraph extract(String fixture) {
        return extractor.fromSpans(TraceSpans.parse(readFixture(fixture)));
    }

    private static String readFixture(String path) {
        try (var in = ServiceGraphExtractorTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
