-- 크루 가입 Redis 상태 사전 초기화 (실험 준비 전용 — 가입 요청 경로에서 호출하지 않는다)
-- KEYS[1] = <prefix>:crew:<crewId>:members (ZSET)
-- KEYS[2] = <prefix>:crew:<crewId>:meta    (HASH)
-- ARGV[1] = capacity (DB max_members, 양의 정수)
-- ARGV[2] = 초기 멤버 userId JSON 배열 (바이트 오름차순 정렬, 중복 없음, 1..capacity개)
-- 반환: 'OK' | 'ALREADY_EXISTS_OR_PARTIAL' | 'INVALID_INPUT'
-- 모든 검증은 첫 쓰기 전에 끝낸다. pending에는 접근하지 않는다.

if #KEYS ~= 2 or #ARGV ~= 2 then
	return 'INVALID_INPUT'
end

local base = string.match(KEYS[1], '^(.+):members$')
if not base or KEYS[2] ~= base .. ':meta' then
	return 'INVALID_INPUT'
end

if not string.match(ARGV[1], '^[1-9]%d?%d?%d?%d?%d?$') then
	return 'INVALID_INPUT'
end
local capacity = tonumber(ARGV[1])

local ok, users = pcall(cjson.decode, ARGV[2])
if not ok or type(users) ~= 'table' then
	return 'INVALID_INPUT'
end
local n = #users
local entries = 0
for _ in pairs(users) do
	entries = entries + 1
end
if entries ~= n or n < 1 or n > capacity then
	return 'INVALID_INPUT'
end
for i = 1, n do
	local user = users[i]
	if type(user) ~= 'string' or user == '' then
		return 'INVALID_INPUT'
	end
	-- 엄격한 오름차순이면 중복도 함께 배제된다
	if i > 1 and not (users[i - 1] < user) then
		return 'INVALID_INPUT'
	end
end

if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then
	return 'ALREADY_EXISTS_OR_PARTIAL'
end

for i = 1, n do
	redis.call('ZADD', KEYS[1], i, users[i])
end
-- meta는 마지막에 완성한다 — initialized=1은 members가 다 들어간 뒤에만 보인다
redis.call('HSET', KEYS[2], 'capacity', capacity, 'seq', n, 'initialized', 1)
return 'OK'
