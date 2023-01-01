package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;


    //// 使用 List
    @Override
    public Result selectAllShopType() {
        //1:从redis中先查询店铺类型缓存    客户端第一次请求get为空的 第一次肯定查sql
        String cacheShopTypKey = RedisConstants.CACHE_SHOP_TYP;
        List<String> shopTypeJsonList = stringRedisTemplate.opsForList().range(cacheShopTypKey, 0, -1);


        // 2. 若 Redis 中存在，则将json list集合转换为 Java 对象(List<ShopType>)后返回；
        if (shopTypeJsonList != null && !shopTypeJsonList.isEmpty()) {
            List<ShopType> arList = new ArrayList<>();
            for (String shopTypeJson : shopTypeJsonList) {
                ShopType shopType = JSONUtil.toBean(shopTypeJson, ShopType.class);
                arList.add(shopType);
                //   return Result.ok(arList); 放到这里的话客户端第二次只会从redis中取出一个 前端只展示美食一个 逻辑错误
            }
            return Result.ok(arList);
        }

        // 3. 若 Redis 中不存在，则在数据库中查询查询的是多条记录；
        List<ShopType> shopTypeList = this.query().orderByAsc("sort").list();

        // 4. 若 数据库 中一个也没有，则报错；  集合中一个shopType元素都没有
        if (shopTypeList.isEmpty() || shopTypeList == null) {
            return Result.fail("一个商品都没有");
        }
        // 5. 若 数据库 中存在，则将其返回并存入 Redis 缓存中。
        for (ShopType shopType : shopTypeList) {
            String jsonStr = JSONUtil.toJsonStr(shopType);
            shopTypeJsonList.add(jsonStr);
        }
        stringRedisTemplate.opsForList().rightPushAll(cacheShopTypKey, shopTypeJsonList); //加入到redis缓存
        return Result.ok(shopTypeList);
    }

    // 使用 String    核心可将json字符串list形式  解析为list集合
    //List<ShopType> shopTypeList = JSONUtil.toList(JSONUtil.parseArray(shopTypeJson), ShopType.class);

}
