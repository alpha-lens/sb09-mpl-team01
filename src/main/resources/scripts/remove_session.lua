local watcherId = KEYS[1]

-- 1. 시청 중인 콘텐츠 ID 조회 후 ZSET에서 제거
local contentId = redis.call('get', 'watching:user:' .. watcherId)
if contentId then
    redis.call('zrem', 'watching:content:' .. contentId, watcherId)
end

-- 2. 유저 세션 키 삭제
redis.call('del', 'watching:user:' .. watcherId)
return 1
