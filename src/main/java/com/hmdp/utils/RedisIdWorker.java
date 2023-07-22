package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
//    /**
//     * 开始时间戳
//     */
//    private static final long BEGIN_TIMESTAMP = 1640995200L;
//    /**
//     * 序列号的位数
//     */
//    private static final int COUNT_BITS = 32;
//
//    private StringRedisTemplate stringRedisTemplate;
//
//    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }
//
//    public long nextId(String keyPrefix) {
//        // 1.生成时间戳
//        LocalDateTime now = LocalDateTime.now();
//        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
//        long timestamp = nowSecond - BEGIN_TIMESTAMP;
//
//        // 2.生成序列号
//        // 2.1.获取当前日期，精确到天
//        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
//        // 2.2.自增长
//        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
//
//        // 3.拼接并返回
//        return timestamp << COUNT_BITS | count;
//    }


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //开始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    //序列号位数
    private static final int COUNT_BITS = 32;

    public long nextId(String keyPrefix){
        //生成当前时间戳
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        long stamp = now - BEGIN_TIMESTAMP;
        //生成序列号,redis自增长
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        Long increment = stringRedisTemplate.opsForValue().increment("timeStamp:" + keyPrefix + ":" + date);

        //拼接
        return stamp << COUNT_BITS |increment;
    }

















}
