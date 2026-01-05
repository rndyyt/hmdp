package com.hmdp.mq;

import com.hmdp.dto.VoucherOrderDTO;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import static com.hmdp.utils.RedisConstants.LOCK_ORDER_KEY;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "seckill_order",consumerGroup = "seckill_order_group")
public class SeckillConsumer implements RocketMQListener<VoucherOrderDTO> {
    private final RedissonClient redissonClient;
    private final IVoucherOrderService voucherOrderService;
    @Override
    public void onMessage(VoucherOrderDTO voucherOrderDTO) {
        String lockKey = LOCK_ORDER_KEY + voucherOrderDTO.getUserId();
        RLock lock = redissonClient.getLock(lockKey);
        boolean isLock = lock.tryLock();
        if (!isLock){
            log.warn("并发锁限制");
            return;
        }

        try {
            voucherOrderService.addOrder(voucherOrderDTO.getVoucherId(), voucherOrderDTO.getUserId());
        }finally {
            lock.unlock();
        }

    }
}
