package com.hmdp.utils;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import org.springframework.core.io.PathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author:张鹏
 * @description:
 * @date: 2023/1/8 13:26
 */
public class SimpleDistributedLockBasedOnRedis implements DistributedLock {
    private String name;//锁地名称
    private StringRedisTemplate stringRedisTemplate;

    public SimpleDistributedLockBasedOnRedis(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";  //true去掉产生的-
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    //读取脚本文件
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript();
        UNLOCK_SCRIPT.setLocation(new PathResource("unlock.lua"));  //脚本文件的位置
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    /**
     * 获取锁 只获取一次
     *
     * @param timeoutSeconds 锁的超时时间，过期后自动释放
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSeconds) {
        //获取当前线程Id
        String threadId = ID_PREFIX + Thread.currentThread().getId();   //每个线程都有唯一的线程Id
        //参数：用setnx来设置(锁的)key和value   time timeunit 利用setnx的特点如果有线程已经setnx key1了
        // 那么其他线程不能setcx key1了 必须等他过期，或者删掉key
         Boolean isSucceeded = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "", timeoutSeconds, TimeUnit.SECONDS);
         //lock:order:10      dubge看
         return Boolean.TRUE.equals(isSucceeded);
        //避免isSucceeded是null 如果这里isSucceeded是null返回false 直接返回可能空指针  应为返回是基本类型boolean isSucceeded是包装类这里会自动拆箱若果isSucceeded为空，直接空指针
    }

/*
    @Override
    public void unlock() {
        // 获取线程标识
        String threadIdentifier = ID_PREFIX + Thread.currentThread().getId();
        //获取锁中的标识
        String threadIdentifierFromRedis = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        // 比较 锁中的线程标识 与 当前的线程标识 是否一致
        if (StrUtil.equals(threadIdentifier, threadIdentifierFromRedis)) {
            // 释放锁标识
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
*/

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        // 调用 Lua 脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,  // SCRIPT   静态代码块读取脚本文件返回的对象
                Collections.singletonList(KEY_PREFIX + name),   // KEY[1]=lock+锁的名称  将string转为集合类型  Lua脚本第一个索引为1没有0
                ID_PREFIX + Thread.currentThread().getId()    // ARGV[1]=
        );
    }



}
