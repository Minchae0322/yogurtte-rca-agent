// K. 대용량 업로드 - 요청 수가 아니라 요청 "크기"로 죽는 경로
//
// max-file-size / max-request-size 가 1GB 로 열려 있다(세 서비스 공통 application.yml).
// 동시에 몇 개만 들어와도 톰캣 스레드가 업로드 시간 내내 점유되고, 임시 파일이 디스크를 먹는다.
// 요청 수 기준 부하테스트로는 절대 안 잡히는 종류의 한계다.
//
// 기본값은 작게 잡았다. 올릴 때는 k6 쪽 메모리(파일 크기 x VU)를 먼저 계산할 것.
//   k6 run autoscaling/loadtest/k-upload.js -e SIZE_MB=10 -e VUS=20
//
// 볼 것: tomcat_threads_busy(업로드 시간만큼 점유) · 디스크 사용량과 임시파일 정리 여부 ·
//        업로드 중 다른 API 의 p99(스레드 굶주림). 운영 환경에서는 돌리지 말 것.
import http from 'k6/http';
import { check } from 'k6';
import { CONTENT, tokenPool, bearer, pick } from './lib/common.js';

const SIZE_MB = Number(__ENV.SIZE_MB || 5);
const VUS = Number(__ENV.VUS || 10);

// init 컨텍스트에서 1회 생성. VU 마다 복사되므로 SIZE_MB x VUS 만큼 k6 메모리를 쓴다.
const payload = 'x'.repeat(SIZE_MB * 1024 * 1024);

export const options = {
  vus: VUS,
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

export function setup() {
  return { tokens: tokenPool(10) };
}

export default function (data) {
  const res = http.post(
    `${CONTENT}/attachment-file/upload`,
    { file: http.file(payload, `k6-${__VU}-${__ITER}.bin`, 'application/octet-stream') },
    { headers: bearer(pick(data.tokens, __VU)), tags: { name: 'POST /attachment-file/upload' }, timeout: '120s' }
  );
  check(res, { 'upload 2xx': (r) => r.status >= 200 && r.status < 300 });
}
