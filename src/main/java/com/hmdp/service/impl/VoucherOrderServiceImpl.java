package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RedisUtils;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.hmdp.utils.RedisConstants.ORDER_LOCK_USER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final RedisUtils redisUtils;

    @Override
    public Result addSeckillVoucherOrder(Long voucherId) {
        // 手写redis+lua实现分布式锁，解决一人一单问题
        // 首先确定锁粒度为用户ID
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();
        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        if(voucher.getBeginTime().isAfter(now)) {
            return Result.fail("秒杀券尚未开始");
        }
        if(voucher.getEndTime().isBefore(now)) {
            return Result.fail("秒杀券已结束");
        }
        if(voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        boolean lock = redisUtils.tryLock(ORDER_LOCK_USER_KEY + userId);
        if (!lock){
            return Result.fail("请勿重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.addOrder(voucherId,userId);
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            redisUtils.unlock(ORDER_LOCK_USER_KEY + userId);
        }
    }

    @NotNull
    @Transactional
    public Result addOrder(Long voucherId, Long userId) {
        Long cnt = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if(cnt > 0){
            return Result.fail("已购买");
        }
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success){
            return Result.fail("库存不足");
        }
        Long orderId = redisIdWorker.nextId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        this.save(voucherOrder);

        return Result.ok(orderId);
    }
}
