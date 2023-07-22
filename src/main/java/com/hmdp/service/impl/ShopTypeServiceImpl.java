package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPELIST_KEY;

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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryShoplist() {
        //从redis查商铺列表缓存
        List<String> shopTypeList = stringRedisTemplate.opsForList().range(CACHE_SHOPTYPELIST_KEY, 0, 9);
        //判断redis是否存在
        if(shopTypeList != null && !shopTypeList.isEmpty()){
            //存在，返回缓存,shopTypeList.get(0)-->结构是[a,b,c,...]
            log.debug("查的是redis");
            return Result.ok(JSONUtil.toList(shopTypeList.get(0),ShopType.class));
        }
        //不存在，去数据库查
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        if (shopTypes.isEmpty()){
            //不存在，返回fail
            Result.fail("暂无商家类型！");
        }
        //存在，先返回给redis
        stringRedisTemplate.opsForList().rightPush(CACHE_SHOPTYPELIST_KEY,JSONUtil.toJsonStr(shopTypes));
        //再返回前端
        return Result.ok(JSONUtil.toList(shopTypeList.get(0),ShopType.class));
    }
}
