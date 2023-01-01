package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

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

    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        //1:从redis中先查询店铺缓存    客户端第一次请求get为空的
        ValueOperations<String, String> stringValueOperations = stringRedisTemplate.opsForValue();
        String shopJson = stringValueOperations.get(key);

        // 2. 若 Redis 中存在，则将其转换为 Java 对象后返回；
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);  //反序列化 将json数据 转为shop对象
            return Result.ok(shop);
        }

        // 3. 若 Redis 中不存在，则根据 id 从数据库中查询；
        Shop shop = this.getById(id);

        // 4. 若 数据库 中不存在，则报错；
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 5. 若 数据库 中存在，则将其返回并存入 Redis 缓存中。 存的是字符串
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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
