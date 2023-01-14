package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.TTL_TWO;

/**
 * @author:张鹏
 * @description: 基于缓存的工具类
 * @date: 2023/1/1 19:58
 */

@Slf4j        //日志
@Component    //这个类让spring去维护
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意 Java 对象序列化为 JSON 存储在 String 类型的 Key 中，并且可以设置 TTL 过期时间
     * @param key    键
     * @param value  任意对象
     * @param time  时间
     * @param unit  时间的单位
     */
   //用户可以传任何类型的对象 Object去接受 接受任意类型的对象
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }


    /**
     *将任意 Java 对象序列化为 JSON 存储在 String 类型的 Key 中，并且可以设置逻辑过期时间，用于处理缓存击穿
     * @param key    键
     * @param value  任意对象
     * @param time  时间
     * @param unit  时间的单位
     */

    public  void  setWithLogicalExpiration(String key,Object value,Long time,TimeUnit unit){
        //封装对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpiretime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //d返回值不确定一般用泛型 <R> R 返回值必须这么写  也可Object类型但是泛型出错会在编译器报错 object出错就是运行时异常
    /**  <R,ID>先要指定的泛型  在参数id传过来的不一定是Long type类型也不知道
     * Class<R> type 传参： 类名.class
     * 到时候传参的时候我们需要指定泛型 id type形参 实参比如为3L Shop.class 此时自动推断ID为String R为Shop
     *
     * Shop shop = cacheClient.dealWithCachePenetration(CACHE_SHOP_KEY, id, Shop.class,
     * this::getById, TTL_THIRTY, TimeUnit.MINUTES);
     *
     * @param keyPrefix CACHE_SHOP_KEY  String
     * @param id           id           String
     * @param type          Shop.class
     * @param dbFallback
     * @param time           TTL_THIRTY
     * @param timeUnit     TimeUnit.MINUTES
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R dealWithCachePenetration(String keyPrefix, ID id, Class<R> type, Function<ID, R>
            dbFallback, Long time, TimeUnit timeUnit){
        String key = keyPrefix + id;
        // 1. 从 Redis 中查询店铺缓存；
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 若 Redis 中存在（命中），则将其转换为 Java 对象后返回；
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);  //type=Shop.class
        }
        // 3. 命中缓存后判断是否为空值
        if (ObjectUtil.equal(json, "")) {
            return null;
        }
        // 4. 若 Redis 中不存在（未命中），则根据 id 从数据库中查询；    R就是到时候传参的Shop类
        R r = dbFallback.apply(id);   //这里这么久根据id查数据库了呢？？？？？？？this.getById()??执行了？？？？？

        // 5. 若 数据库 中不存在，将空值写入 Redis（缓存空对象）
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", TTL_TWO, TimeUnit.MINUTES);
            return null;
        }

        // 6. 若 数据库 中存在，则将其返回并存入 Redis 缓存中。
        this.set(key, r, time, timeUnit);
        return r;

    }

    /**
     * 根据指定的 Key 查询缓存，反序列化为指定类型，利用逻辑过期的方式解决缓存击穿问题。
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    public <R, ID> R dealWithCacheHotspotInvalid(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1. 从 Redis 中查询店铺缓存；
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 未命中
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. 命中（先将 JSON 反序列化为 对象）
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpiretime();
        // 4. 判断是否过期：未过期，直接返回店铺信息；过期，需要缓存重建。
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        // 5. 缓存重建（未获取到互斥锁，直接返回店铺信息）
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLocked = tryLock(lockKey);
        // 5.1 获取到互斥锁
        // 开启独立线程：根据 id 查询数据库，将数据写入到 Redis，并且设置逻辑过期时间。
        // 此处必须进行 DoubleCheck：多线程并发下，若线程1 和 线程2都到达这一步，线程1 拿到锁，进行操作后释放锁；线程2 拿到锁后会再次进行查询数据库、写入到 Redis 中等操作。
        json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        if (isLocked) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    R apply = dbFallback.apply(id);
                    this.setWithLogicalExpiration(key, apply, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        // 5.2 返回店铺信息
        return r;
    }

    /**
     * 获取互斥锁
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.TTL_TEN, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
