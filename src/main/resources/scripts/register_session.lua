local watcherId = KEYS[1]
local contentId = KEYS[2]
local now = tonumber(ARGV[1])

-- 1. 기존 시청 세션이 있는지 확인 후 ZSET에서 제거
local oldContentId = redis.call('get', 'watching:user:' .. watcherId)
if oldContentId then
    redis.call('zrem', 'watching:content:' .. oldContentId, watcherId)
end

-- 2. 새 세션 등록 및 TTL 설정
redis.call('setex', 'watching:user:' .. watcherId, 300, contentId)
redis.call('zadd', 'watching:content:' .. contentId, now, watcherId)
return 1
