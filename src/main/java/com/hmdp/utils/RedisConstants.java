package com.hmdp.utils;

public class RedisConstants {
    //验证码
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    //token
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;
    //redis中空值缓存时长
    public static final Long CACHE_NULL_TTL = 2L;

    //商铺详情
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final Long CACHE_SHOP_TTL = 30L;

    //商铺类型列表
    public static final String CACHE_SHOPTYPELIST_KEY = "cache:shop-type:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
}
