-- =============================================================
-- deduct_stock.lua — Redis 库存原子性预扣减
-- KEYS[1]: 商品库存 key，如 seckill:stock:1
-- ARGV[1]: 扣减数量
-- 返回值: 扣减后剩余库存（>=0 成功），-1 表示库存不足
-- =============================================================
local key = KEYS[1]
local amount = tonumber(ARGV[1])

-- 读取当前库存
local stock = tonumber(redis.call('GET', key) or '0')

if stock <= 0 or stock < amount then
    return -1
end

-- 原子性扣减
local remaining = redis.call('DECRBY', key, amount)
return remaining
