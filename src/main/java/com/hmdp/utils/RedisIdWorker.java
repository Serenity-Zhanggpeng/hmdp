package com.hmdp.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author:张鹏
 * @description:
 * @date: 2023/1/3 18:10
 */

@Component        //交给spring去管理 以后用的时候就可以直接注入了
public class RedisIdWorker {

    /**
     * LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
     * 2022年1月1日 0:0:00 的时间戳     时间戳就是将时间转为秒在以long型去存储
     */
    private static final long BEGIN_TIMESTAMP_2022 = 1640995200L;
    /**
     * 序列号的位数
     */
    private static final int BITS_COUNT = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //返回 由1位符号位 31位时间戳+32位序列号构成的id
    public long nextId(String keyPrefix) {
        // 1. 时间戳
        long currentTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long timestamp = currentTimestamp - BEGIN_TIMESTAMP_2022;   //当前时间戳-2022 1 1的时间戳


        String formatTime = DateTimeFormatter.ofPattern("yyyy:MM:dd").format(LocalDateTime.now());

        //increment这里没指定value默认为0 且key如果不存在默认value值为0 每次执行该语句value的值就会自增1
        //应为key 由icr:" + keyPrefix + ":"+formatTime 只有formatTime可变 每天dd不同都不同，所以每天的key都不同
        //每天下单 存储的key都不一样，  还可以统计每天下的单数， 如果不指定formatTime在key中，以后key都会相同，在incr时总有一天
        //redis会value值会达到最大值的。 而且还不能统计每一天下的单数       increment就是个计数器
        //这个key下单的上线就是每天的下单量不会超过2的32次方
        // 2. 序列号 32位
        long serialNumber = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + formatTime);

        // 3. 拼接（时间戳向左移 32 位，通过或运算将其与序列号拼接）  应为返回的时long型不是简单的字符串拼接  看那个图
        return timestamp << BITS_COUNT | serialNumber;
    }

    //public static void main(String[] args) {
    //toEpochSecond得到当前时间转为以秒位单位,    ZoneOffset.UTC时区
    //    long timestamp = LocalDateTime.of(2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
    //    System.out.println(timestamp);
    //
    //    String formatTime = DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDateTime.now());
    //    System.out.println(formatTime);
    //}
}



