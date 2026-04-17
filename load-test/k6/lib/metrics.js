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
export const joinFull = new Counter('join_full');        // 409
