package com.hmdp;

import cn.hutool.core.date.StopWatch;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class HmDianPingApplicationTest {
    ExecutorService es = Executors.newFixedThreadPool(500);
    @Resource
    RedisIdWorker redisIdWorker;

    @Test
    void IdGenTest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(100);

        Runnable task = ()->{
            for(int i = 0;i<100;i++){
                long id = redisIdWorker.nextId("order");
                System.out.println("id="+id);
            }
            latch.countDown();
        };
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        for(int j=0;j<100;j++){
            es.submit(task);
        }
        latch.await();
        stopWatch.stop();
        System.out.println("Total Times: "+ stopWatch.getTotal(TimeUnit.MILLISECONDS) + "ms");
    }
}
