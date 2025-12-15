package com.hmdp.config;

import com.hmdp.service.IShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class CacheWarmUpRunner implements ApplicationRunner {
    private final IShopService shopService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info(">>>开始缓存预热...");
        shopService.saveShop2Redis(1L,10L);
    }
}
