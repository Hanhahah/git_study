package com.hmdp.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.Synchronized;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券,注意是查seckillVoucher
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始！");
        }
        //判断是否结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束！");
        }

        //判断库存是否充足
        if(voucher.getStock()<=0){
            return Result.fail("已抢光！");
        }
        //返回订单id,要保证一人一单，需要加悲观锁
        Long userid = UserHolder.getUser().getId();
        //先获取锁，后创建事务，事务提交之后再释放
        //给用户id加锁，同一用户加同一把锁，不同用户加不同的锁
        synchronized(userid.toString().intern()){
            //获取当前代理对象？？？？
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        long userid = UserHolder.getUser().getId();
        //查询该用户下单该券的数量
        int count = query().eq("user_id", userid).eq("voucher_id", voucherId).count();
        if(count>0){
            return Result.fail("用户已购买过一次！");
        }
        //充足，扣减库存?????用乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock-1")
//                .eq("voucher_id", voucherId)
                .eq("voucher_id", voucherId).gt("stock",0)
                .update();
        //扣减失败
        if(!success){
            return Result.fail("已抢光！");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //存订单id，用户id，代金券id
        long orderid = redisIdWorker.nextId("order");
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setId(orderid);
        voucherOrder.setUserId(userid);
        save(voucherOrder);
        return Result.ok(orderid);
    }
}
