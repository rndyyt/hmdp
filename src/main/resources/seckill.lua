-- 参数列表：秒杀券库存key，用户ID，优惠券SET的key
local voucherStockKey = KEYS[1]
local voucherSetKey = KEYS[2]
local userId = ARGV[1]

-- 判断库存是否足够
if (tonumber(redis.call('get', voucherStockKey)) <= 0) then
    return 1
end
-- 判断用户是否已经下单
if (redis.call('sismember', voucherSetKey, userId) == 1) then
    return 2
end
-- 执行下单逻辑
redis.call('decr', voucherStockKey)
redis.call('sadd', voucherSetKey, userId)
return 0


