import { Trend, Counter } from 'k6/metrics';

// --- Scenario duration (p95/p99 per scenario) ---
export const scenarioADuration = new Trend('scenario_a_duration', true);
export const scenarioBDuration = new Trend('scenario_b_duration', true);
export const scenarioDDuration = new Trend('scenario_d_duration', true);
// Day 7 — write-heavy 전용 Trend (POST /verifications만 포함, GET 미포함)
export const scenarioWriteDuration = new Trend('scenario_write_duration', true);

// --- Scenario B: write success vs duplicate ---
export const verifyCreated = new Counter('verify_created');     // 201
export const verifyDuplicate = new Counter('verify_duplicate'); // 409

// --- Scenario D: join success vs capacity full ---
export const joinSuccess = new Counter('join_success');  // 201
export const joinFull = new Counter('join_full');        // 409 CR002 정원초과

// --- Scenario D 상세 분해 (전략 A/B/C 비교) ---
export const joinConflict = new Counter('join_conflict'); // 409 CR023 (낙관락 재시도 소진)
export const joinDup      = new Counter('join_dup');      // 409 CR004 (중복 가입)
export const joinError5xx = new Counter('join_5xx');      // 5xx (서버 에러)
export const joinDropped  = new Counter('join_dropped');  // status 0 (연결 실패/리셋)

// --- Scenario E: duplicate join (전략 C 중복 가입 동시성 검증) ---
export const dupJoinSuccess = new Counter('dup_join_success');       // 201 첫 가입
export const dupJoinAlreadyJoined = new Counter('dup_join_already'); // 409 중복 거부
export const scenarioEDuration = new Trend('scenario_e_duration', true);
