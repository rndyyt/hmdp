package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.FilterExpressionType;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * RocketMQ 5.x gRPC 协议
 * 生产者 + 消费者 联合测试
 */
@Slf4j
public class RocketMQTest {

    // 基础配置
    private static final String ENDPOINT = "localhost:8081"; // 连 Proxy
    private static final String TOPIC = "TestTopic";         // 确保控制台已创建
    private static final String CONSUMER_GROUP = "TestGroup"; // 确保控制台已创建

    private ClientServiceProvider provider;
    private PushConsumer pushConsumer;
    private Producer producer;

    // 倒计时锁：我们需要等待 1 条消息被消费
    private CountDownLatch latch;

    @BeforeEach
    public void setUp() {
        provider = ClientServiceProvider.loadService();
        latch = new CountDownLatch(1); // 初始化锁，计数为 1
    }

    @AfterEach
    public void tearDown() throws IOException {
        // 测试结束后，务必关闭资源，否则会占用端口或线程
        if (producer != null) {
            producer.close();
        }
        if (pushConsumer != null) {
            pushConsumer.close();
        }
    }

    @Test
    public void testProduceAndConsume() throws ClientException, InterruptedException {
        ClientConfiguration clientConfiguration = ClientConfiguration.newBuilder()
                .setEndpoints(ENDPOINT)
                .build();

        // --- 1. 先启动消费者 (Consumer) ---
        // 只有先订阅了，发出来的消息才不会丢（对于某些非持久化场景）
        log.info("正在启动消费者...");

        FilterExpression filterExpression = new FilterExpression("*", FilterExpressionType.TAG);

        pushConsumer = provider.newPushConsumerBuilder()
                .setClientConfiguration(clientConfiguration)
                .setConsumerGroup(CONSUMER_GROUP)
                .setSubscriptionExpressions(Collections.singletonMap(TOPIC, filterExpression))
                .setMessageListener(messageView -> {
                    // --- 【消费者回调逻辑】 ---
                    log.info("✅ [消费者] 收到消息: ID={}, Body={}",
                            messageView.getMessageId(),
                            StandardCharsets.UTF_8.decode(messageView.getBody()));

                    // 关键点：收到消息后，让锁减 1
                    latch.countDown();

                    return ConsumeResult.SUCCESS;
                })
                .build();

        log.info("消费者启动成功，开始监听...");

        // --- 2. 再启动生产者 (Producer) ---
        log.info("正在启动生产者...");
        producer = provider.newProducerBuilder()
                .setClientConfiguration(clientConfiguration)
                .setTopics(TOPIC)
                .build();

        // --- 3. 发送消息 ---
        String body = "Hello E2E Test!";
        Message message = provider.newMessageBuilder()
                .setTopic(TOPIC)
                .setTag("TagA")
                .setBody(body.getBytes(StandardCharsets.UTF_8))
                .build();

        SendReceipt receipt = producer.send(message);
        log.info("📤 [生产者] 发送成功: ID={}", receipt.getMessageId());

        // --- 4. 阻塞等待 ---
        // 主线程在这里卡住，最多等 10 秒
        // 如果 10 秒内消费者回调了 latch.countDown()，这里就会立刻解除阻塞，继续往下走
        boolean received = latch.await(15, TimeUnit.SECONDS);

        // --- 5. 断言验证 ---
        Assertions.assertTrue(received, "测试失败：15秒内消费者未收到消息！(请检查 Broker/Proxy 是否正常)");
        log.info("🎉 测试通过！发送与消费链路打通。");
    }
}