
--原子性 代码要么都成功要么都失败

-- 锁的 Key
-- local key = "lock:order:10"
-- local key = KEYS[1]          KEY[1]的值是在脚本对象中传递的参数在SimpleDistributedLockBasedOnRedis中

-- 最初存入 Redis 中的线程标识
-- local threadIdentifier = "uuid-http-nio-8081-exec-1"
-- local threadIdentifier = ARGV[1]

-- 锁中的线程标识
--local threadIdentifierFromRedis = redis.call('get', KEYS[1])
--
---- 比较 最初存入 Redis 中的线程标识 与 目前 Redis 中存储的线程标识 是否一致
--if (threadIdentifierFromRedis == ARGV[1]) then
--    -- 一致，则释放锁 del key
--    return redis.call('del', KEYS[1])
--end
---- 若不一致，则返回 0
--return 0

-- 终极版
if (redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0
