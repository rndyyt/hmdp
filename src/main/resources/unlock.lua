-- KEYS[1]: 锁的 key
-- ARGV[1]: 当前线程的标识 (你的 threadId)

-- 1. 获取锁当前的值，判断是否等于我的 threadId
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    -- 2. 如果相等，则删除锁
    return redis.call('del', KEYS[1])
else
    -- 3. 如果不相等，返回 0 (什么都不做)
    return 0
