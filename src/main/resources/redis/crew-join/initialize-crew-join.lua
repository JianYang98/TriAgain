-- 크루 가입 Redis 상태 사전 초기화 (실험 준비 전용 — 가입 요청 경로에서 호출하지 않는다)
-- KEYS[1] = <prefix>:crew:<crewId>:members (ZSET)
-- KEYS[2] = <prefix>:crew:<crewId>:meta    (HASH)
-- ARGV[1] = capacity (DB max_members, 양의 정수)
-- ARGV[2] = 초기 멤버 userId JSON 배열 (비어 있지 않은 문자열, 중복 없음, 1..capacity개)
--           score는 배열 순서대로 1..N. 재현 가능한 배치를 위해 결정적 순서로 배열을 만드는 것은
--           준비 도구 책임이며, 이 스크립트는 배열 순서를 불변식으로 검사하지 않는다.
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
local seen = {}
for i = 1, n do
	local user = users[i]
	if type(user) ~= 'string' or user == '' then
		return 'INVALID_INPUT'
	end
	-- 순서는 검사하지 않는다(Lua 문자열 비교는 locale 의존). 중복만 배제한다
	if seen[user] then
		return 'INVALID_INPUT'
	end
	seen[user] = true
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
