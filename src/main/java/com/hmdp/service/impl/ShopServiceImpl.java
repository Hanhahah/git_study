package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.hmdp.utils.RedisConstants;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryByid(Long id) {
        //解决缓存穿透问题
        //return queryWithPassThrough(id);

        //通过用setnx模拟互斥锁解决缓存击穿问题
        //Shop shop = queryWithMutex(id);

        //通过逻辑超时解决缓存击穿问题
        Shop shop = queryWithLogicalExpire(id);

        if(shop==null){
            return Result.fail("店铺信息不存在！");
        }
        //
        return Result.ok(shop);
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //通过逻辑过期进行缓存重建
    public Shop queryWithLogicalExpire(Long id) {
        String key = LOCK_SHOP_KEY+id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//        //2.判断redis是否存在(!注意json的为空判断）
        if (StrUtil.isBlank(shopJson)){
            //不存在，返回空对象
            this.saveShop2Redis(id,20L);
            return getById(id);
        }

        //存在，判断redis中逻辑时间是否超时（缓存是否过期）
        //将json数据转化为对象,取出time
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        //注意转化为JSONObject!!再转化为对象
        Shop shop = JSONUtil.toBean((JSONObject)redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //未过期，返回shop信息
        if(expireTime.isAfter(LocalDateTime.now())){
            return shop;
        }
        //过期，获取互斥锁
        boolean isLock = getLock(key);
        //获取锁成功，新建一个线程去将值更新到redis
        if(isLock){
            try {
                //线程提交任务，lamba表达式？？
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    this.saveShop2Redis(id,20L);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                //释放锁
                unLock(key);
            }
        }
        //获取锁失败，返回旧值
        return shop;
    }

    //通过互斥锁进行缓存重建
    public Shop queryWithMutex(Long id) {
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断redis是否存在(!注意json的为空判断）
        if (!StrUtil.isBlank(shopJson)){
            //存在(不为“”），返回店铺缓存
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return shop;
        }
        //命中空值“”，返回提示信息,注意判空操作不可以==“”
        if(shopJson!=null){
            return null;
        }

        //不存在，先获取互斥锁
        String lockKey = null;
        Shop shop = null;
        try {
            lockKey = "lock:shop:"+id;
            Boolean lock = getLock(lockKey);
            //未得到锁，先短暂休眠，再查redis
            if(!lock){
                Thread.sleep(50);
                //递归，记得加return
                return queryWithMutex(id);
            }

            //得到锁，查数据库是否存在
            shop = getById(id);
            if (shop==null){
                //不存在，将""值写入redis
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                //返回fail
                return null;
            }
            //存在，先往redis存
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放锁
            unLock(lockKey);
        }
        //再返回
        return shop;
    }

    public Result queryWithPassThrough(Long id) {
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2.判断redis是否存在(!注意json的为空判断）
        if (!StrUtil.isBlank(shopJson)){
            //存在(不为“”），返回店铺缓存
            Shop shop = JSONUtil.toBean(shopJson,Shop.class);
            return Result.ok(shop);
        }
        //不存在，且命中空值“”，返回提示信息,注意判空操作不可以==“”
        if(shopJson!=null){
            return Result.fail("店铺信息不存在！");
        }

        //不存在，且redis中无“”，查数据库(mybatisPlus)
        Shop shop = getById(id);
        //数据库是否存在
        if (shop==null){
            //不存在，将""值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            //返回fail
            return Result.fail("商铺不存在！");
        }
        //存在，先往redis存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        //再返回
        return Result.ok(shop);
    }

    //获取锁
    private Boolean getLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    //将shop信息和自定义的expiretime存入redis
    public void saveShop2Redis(Long id,Long expireSeconds){
        RedisData redisData = new RedisData();
        //先查询数据库
        Shop shop = getById(id);
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    //保证事务原子性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id==null){
            Result.fail("店铺id不能为空！");
        }

        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }
}
