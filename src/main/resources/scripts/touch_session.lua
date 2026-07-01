local watcherId = KEYS[1]
local contentId = KEYS[2]
local now = tonumber(ARGV[1])

-- 1. 유저 키 존재 여부 확인 및 TTL 연장 (exists와 expire를 단일 expire 호출로 최적화)
local ttlResult = redis.call('expire', 'watching:user:' .. watcherId, 300)
if ttlResult == 1 then
    -- 2. Sorted Set에서 Score를 현재 시간으로 갱신 (존재하며 기존 점수보다 클 때만)
    redis.call('zadd', 'watching:content:' .. contentId, 'XX', 'GT', now, watcherId)
    return 1
end
return 0
