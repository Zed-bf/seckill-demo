-- =============================================================
-- rollback_stock.lua — Redis 库存原子性回滚
-- KEYS[1]: 商品库存 key
-- ARGV[1]: 回滚数量
-- 返回值: 回滚后库存数
-- =============================================================
local key = KEYS[1]
local amount = tonumber(ARGV[1])

local remaining = redis.call('INCRBY', key, amount)
return remaining
