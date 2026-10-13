-- 공개 크루 가입 Redis 승인 + pending 등록 (원자 실행. 실행 오류 시 이전 쓰기를 rollback하지 않는다)
-- KEYS[1] = <prefix>:crew:<crewId>:members (ZSET)
-- KEYS[2] = <prefix>:crew:<crewId>:meta    (HASH: capacity, seq, initialized)
-- KEYS[3] = <prefix>:pending               (LIST 또는 없음)
-- ARGV[1] = crewId, ARGV[2] = userId, ARGV[3] = confirmedAt(+09:00, 밀리초), ARGV[4] = payload JSON
-- 반환: 항상 {code, currentMembers}. 0 JOIN_SUCCESS(승인 후 인원) / 1 ALREADY_JOINED / 2 CREW_FULL /
--       3 NOT_INITIALIZED / 4 INVALID_STATE — 1~4는 쓰기 전 판정이며 어떤 key도 바꾸지 않는다.
-- 첫 쓰기(HINCRBY) 이후의 예상 밖 결과는 정상 code가 아니라 error reply(실행 오류)다.

local JOIN_SUCCESS, ALREADY_JOINED, CREW_FULL, NOT_INITIALIZED, INVALID_STATE = 0, 1, 2, 3, 4

local function reject(code)
	return {code, 0}
end

-- 양의 정수 문자열만 허용. 15자리 이하 = seq+1이 ZSET score(double)의 정확한 정수 범위 안
local function positive_int(value)
	if type(value) ~= 'string' or not string.match(value, '^[1-9]%d*$') or #value > 15 then
		return nil
	end
	return tonumber(value)
end

-- 1. 인수·key 이름·payload (쓰기 전)
-- KEY와 ARGV 갯수 확인
if #KEYS ~= 3 or #ARGV ~= 4 then
	return reject(INVALID_STATE)
end
for i = 1, 4 do
	if ARGV[i] == '' then
		return reject(INVALID_STATE)
	end
end
local prefix = string.match(KEYS[3], '^(.+):pending$')
if not prefix -- lua는 무슨 값이 뭔지 모르기 때문에, 값이 일치하는지 검증한다.
		or KEYS[1] ~= prefix .. ':crew:' .. ARGV[1] .. ':members'
		or KEYS[2] ~= prefix .. ':crew:' .. ARGV[1] .. ':meta' then
	return reject(INVALID_STATE)
end
local decoded, payload = pcall(cjson.decode, ARGV[4]) -- pcall은 루아의 트라이캐치  docoded 성공은 true, 실패는 false
if not decoded or type(payload) ~= 'table' then
	return reject(INVALID_STATE)
end
local fields = 0
for _, value in pairs(payload) do
	fields = fields + 1
	if type(value) ~= 'string' then
		return reject(INVALID_STATE)
	end
end
if fields ~= 3 or payload.crewId ~= ARGV[1] or payload.userId ~= ARGV[2] or payload.confirmedAt ~= ARGV[3] then
	return reject(INVALID_STATE)
end

-- 2. 모든 key type 체크!
local membersType = redis.call('TYPE', KEYS[1]).ok
local metaType = redis.call('TYPE', KEYS[2]).ok
local pendingType = redis.call('TYPE', KEYS[3]).ok
if pendingType ~= 'none' and pendingType ~= 'list' then
	return reject(INVALID_STATE)
end
if membersType == 'none' and metaType == 'none' then
	return reject(NOT_INITIALIZED)
end
if membersType ~= 'zset' or metaType ~= 'hash' then
	return reject(INVALID_STATE)
end

-- 3. 초기화 완료·capacity/seq·members 일관성 (가입 추가 전용 Phase: seq = ZCARD = 최대 score)
local meta = redis.call('HMGET', KEYS[2], 'initialized', 'capacity', 'seq') -- 데이터 가져와서 meta에 담아
local capacity = positive_int(meta[2])
local seq = positive_int(meta[3])
if meta[1] ~= '1' or not capacity or not seq or seq > capacity then
	return reject(INVALID_STATE)
end
local memberCount = redis.call('ZCARD', KEYS[1]) -- 이 zset의 크루 몇명이야
local top = redis.call('ZRANGE', KEYS[1], -1, -1, 'WITHSCORES') -- 그 크루 맨 마지막 사람
if memberCount ~= seq or tonumber(top[2]) ~= seq then
	return reject(INVALID_STATE)
end

-- 4. 중복 체크, 정원 체크  (ZSCORE는 미존재면 false. score 값으로 판정하지 않는다)
if redis.call('ZSCORE', KEYS[1], ARGV[2]) ~= false then
	return reject(ALREADY_JOINED)
end
if memberCount >= capacity then
	return reject(CREW_FULL)
end

--------------------------------------------------

-- 5. 쓰기. 여기부터의 이상은 실행 오류 — 일부 기록이 남을 수 있다
local nextSeq = redis.call('HINCRBY', KEYS[2], 'seq', 1) -- 시퀀스 올리기! 승인 순번 +1
if nextSeq ~= seq + 1 then
	return redis.error_reply('CREW_JOIN_WRITE_FAILED: seq ' .. seq .. ' -> ' .. tostring(nextSeq))
end
local added = redis.call('ZADD', KEYS[1], 'NX', nextSeq, ARGV[2]) -- 크루에 멤버 추가하기
if added ~= 1 then
	return redis.error_reply('CREW_JOIN_WRITE_FAILED: ZADD NX added ' .. tostring(added))
end
redis.call('LPUSH', KEYS[3], ARGV[4]) -- pending큐에 넣기
return {JOIN_SUCCESS, memberCount + 1}
