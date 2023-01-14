package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //法二直接用封装的工具类
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = dealWithCachePenetrationByNullValue(id);
        Shop shop = cacheClient.dealWithCachePenetration(CACHE_SHOP_KEY, id, Shop.class, id1 -> { //这里的id1是形参,apply调用传的才是实参
            return this.getById(id1);
        }, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //基于互斥锁解决缓存击穿问题
        //Shop shop = dealWithCacheBreakdownByMutex(id);

        //逻辑过期解决 缓存击穿问题
        //Shop shop = queryWithLogicalExpire(id);

        return Result.ok(shop);
    }

    /**
     * 通过缓存空对象解决 Redis 的缓存穿透问题
     */
    public Shop dealWithCachePenetrationByNullValue(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询店铺缓存；
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 若 Redis 中存在（命中），则将其转换为 Java 对象后返回；
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 3. 命中缓存后判断是否为空值
        if (ObjectUtil.equals(shopJson, "")) {
            return null;
        }

        // 4. 若 Redis 中不存在（未命中），则根据 id 从数据库中查询；
        Shop shop = getById(id);

        // 5. 若 数据库 中不存在，将空值写入 Redis（缓存空对象）
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        // 6. 若 数据库 中存在，则将其返回并存入 Redis 缓存中。
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    /**
     * 通过互斥锁解决 Redis 的缓存击穿问题
     */
    public Shop dealWithCacheBreakdownByMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询店铺缓存；
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 若 Redis 中存在（命中），则将其转换为 Java 对象后返回；
        //shopJson三种情况 null   有值  值为空字符串即" " 或xx  null和空字符串的区别
        if (StrUtil.isNotBlank(shopJson)) {   //StrUtil.isNotBlan() 排除等于null和” “的情况
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 3. 命中缓存后判断是否为空值
        if (ObjectUtil.equals(shopJson, "")) {
            return null;
        }

        // 4. 若 Redis 中不存在（缓存未命中），实现缓存重建
        // 4.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLocked = tryLock(lockKey);
            // 4.2 获取失败，休眠重试
            if (!isLocked) {
                Thread.sleep(50);
                return dealWithCacheBreakdownByMutex(id);   //迭代
            }
            // 4.3 获取成功，从数据库中根据 id 查询数据
            shop = getById(id);
            // 4.4 若 数据库 中不存在，将空值写入 Redis（缓存空对象）
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 4.5 若 数据库 中存在，则将其返回并存入 Redis 缓存中。
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 5. 释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    /**
     * 获取互斥锁
     */
    private boolean tryLock(String key) {
        //setIfAbsent redis中是setnx       1 value值随便设的
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    // 重建缓存的方法 sleep 200ms，让一部分线程先于该方法完成查询。（实现 5.5 处，“缓存击穿的解决方案“图片的效果）
    public void saveHotShopInformation2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1. 查询店铺信息
        Shop shop = getById(id);
        Thread.sleep(200);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpiretime(LocalDateTime.now().plusSeconds(expireSeconds)); //plusSeconds加xxs
        // 3. 写入 Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1. 从 Redis 中查询店铺缓存；
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2. 未命中
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // 3. 命中（先将 JSON 反序列化为 对象）
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpiretime();

        // 4. 判断是否过期：未过期，直接返回店铺信息；  过期，需要缓存重建。 过期时间是否在当前时间之后
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期
            return shop;
        }

        //逻辑时间过期
        // 5. 缓存重建（未获取到互斥锁，直接返回店铺信息）
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLocked = tryLock(lockKey);

        // 5.1 获取到互斥锁
        // 开启独立线程：根据 id 查询数据库，将数据写入到 Redis，并且设置逻辑过期时间。
        // 此处必须进行 DoubleCheck：多线程并发下，若线程1 和 线程2都到达这一步，线程1 拿到锁，进行操作后释放锁；线程2 拿到锁后会再次进行查询数据库、写入到 Redis 中等操作。
        shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        if (isLocked) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存  设置逻辑过期时间(在当前的时间上+xx秒)
                    this.saveHotShopInformation2Redis(id, 30L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放互斥锁
                    unLock(lockKey);
                }
            });
        }
        // 5.2 返回店铺信息
        return shop;                          //看图理解·
    }


    @Transactional      //事务控制 原子性 为了防止删除缓存失败 同时又更新数据库了 导致缓存不一致问题
    @Override
    public Result modifyShop(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("店铺ID不能为空");
        }

        //1 修改数据库
        this.updateById(shop);

        //2删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shopId);

        return Result.ok();
    }

}
