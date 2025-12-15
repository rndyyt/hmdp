package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
@Service
@RequiredArgsConstructor
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RedisUtils redisUtils;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 查询redis中是否存在数据
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 如果存在则直接返回
        if(StrUtil.isNotBlank(shopJson)){
            return Result.ok(JSONUtil.toBean(shopJson, Shop.class));
        }
        // 判断shopJson是否为空字符串，如果是则说明是为了防止缓存穿透而存放的空值，直接返回错误
        if (shopJson != null){
            return Result.fail("店铺不存在");
        }
        // redis中不存在则查询数据库
        Shop shop = this.getById(id);
        // 如果数据库中存在则写入redis并返回
        // 为了解决缓存穿透问题，即使不存在也将数据写入redis中
        if(shop != null){
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(shop);
        }else{
            stringRedisTemplate.opsForValue().set(key,"",2,TimeUnit.MINUTES);
        }
        // 如果不存在则返回错误
        return Result.fail("店铺不存在");
    }

    @Override
    public void editById(Shop shop) {
        // 更新数据库
        this.updateById(shop);

        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
    }

    @Override
    public Result queryByMutex(Long id) {
        Shop shop = redisUtils.queryWithMutex(CACHE_SHOP_KEY,id,Shop.class,()->getById(id));
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
//        String key = CACHE_SHOP_KEY + id;
//        // 首先查询redis中是否存在数据
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 如果不为空则直接返回
//        if(StrUtil.isNotBlank(shopJson)){
//            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
//        }
//        // 如果不为null则说明是为了防止缓存穿透而设置的空值
//        if(shopJson != null){
//            return Result.fail("店铺不存在");
//        }
//        // 未命中缓存则需要获取互斥锁
//        String lockKey = CACHE_MUTEX_KEY + id;
//        // 为了防止误删锁问题，添加ThreadId验证
//        String threadId = "thread-" + Thread.currentThread().getId();
//        if(!tryLock(lockKey,threadId)){
//            // 如果未获取到互斥锁则休眠后重新查询
//            try {
//                TimeUnit.MILLISECONDS.sleep(100);
//                return queryByMutex(id);
//            } catch (InterruptedException e) {
//                throw new RuntimeException("休眠线程被打断",e);
//            }
//        }
//        try {
//            // 获取到互斥锁后,为了防止其他线程已经完成查询了，先查redis再查数据库
//            String doubleCheck = stringRedisTemplate.opsForValue().get(key);
//            if(StrUtil.isNotBlank(doubleCheck)){
//                return Result.ok(JSONUtil.toBean(doubleCheck,Shop.class));
//            }
//            if(doubleCheck != null){
//                return Result.fail("店铺不存在");
//            }
//            Shop shop = this.getById(id);
//            // 如果数据库中不存在则写入空值到Redis并返回错误
//            if(shop == null){
//                stringRedisTemplate.opsForValue().set(key,"",3,TimeUnit.MINUTES);
//                return Result.fail("店铺不存在");
//            }
//            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);
//            return Result.ok(shop);
//        } catch (Exception e){
//            throw new RuntimeException(e);
//        } finally {
//            // 解锁操作放在finally中预防死锁
//            unlock(lockKey,threadId);
//        }

    }

    @Override
    public Result queryWithLogicalExpire(Long id) {
        Shop shop = redisUtils.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,()->getById(id));
        if(shop == null){
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    public void saveShop2Redis(Long id,Long expireSecond) {
        // 查询店铺数据
        Shop shop = getById(id);
        // 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        // 保存到缓存
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }

    private void unlock(String lockKey, String tId) {
        String curId = stringRedisTemplate.opsForValue().get(lockKey);
        if(tId.equals(curId)){
            stringRedisTemplate.delete(lockKey);
        }
    }

    private boolean tryLock(String lockKey,String tId) {
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,tId,10,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);
    }
}
