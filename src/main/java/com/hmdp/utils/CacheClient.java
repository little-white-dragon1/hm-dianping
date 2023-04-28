package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    //创建一个具有10个线程池的
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData), time, unit);
    }

    public <R,ID> R LogicalExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbFallback,Long time, TimeUnit unit) {
        //拼接key的值
        String key = CACHE_SHOP_KEY + id;
        //根据id从redis中取
        String Json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在，不存在返回null，
        if (StrUtil.isBlank(Json)) {
            return null;
        }
        //存在,反序列为redisData
        RedisData redisData = JSONUtil.toBean(Json,RedisData.class);
        //最好不要强转，否则容易出问题
        JSONObject data = (JSONObject)redisData.getData();
        R r = JSONUtil.toBean(data,type);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断日期是都已经过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回\
            return r;
        }
        //已过期，重建缓存
        //拼接互斥锁的key
        String LockKey = LOCK_SHOP_KEY +id;
        //获取互斥锁
        boolean isLock = tryLock(LockKey);
        if (isLock) {
            // TODO 互斥锁获取成功.开启独立线程进行缓存
            executorService.submit(() ->{
                try {
                    //查询数据库
                    R r1 = dbFallback.apply(id);
                    //重建缓存
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放互斥锁
                    this.delete(LockKey);
                }

            });
        }
        //没有获取成功就直接返回旧的商铺信息
        return r;
    }

//    public Shop Mutex(Long id) {
//        //拼接key的值
//        String key = CACHE_SHOP_KEY + id;
//        //根据id从redis中取
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //isNotBlank若字符串为"",null,"\t\n"会false，若为"abc"则为true；
//        if (StrUtil.isNotBlank(shopJson)) {
//            //反序列化
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //判断是否命中的缓存中的空数据（空数据是为了防止缓存穿透效果的一个策略）
//        if (shopJson != null) {
//            return null;
//        }
//        //拼接key
//        String LockKey = LOCK_SHOP_KEY + id;
//
//        Shop shop = null;
//        try {
//            //获取锁
//            boolean b = tryLock(LockKey);
//            //判断是否获取成功
//            if(!b){
//                //没有获取成功，休眠等待其他进程修改完毕
//                Thread.sleep(50);
//                //休眠结束后再次尝试从缓存中读取(递归
//                return Mutex(id);
//            }
//            //若获取成功了,就去数据库中查找
//            shop = getById(id);
//            //若数据库中没有
//            if (shop == null) {
//                //在缓存中写入有个空数据，防止缓存穿透
//                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            //若数据库中有
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            //释放锁
//            delete(LockKey);
//        }
//        return shop;
//    }

    public <R, ID> R PassThrought(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //拼接key的值
        String key = keyPrefix + id;
        //根据id从redis中取
        String json = stringRedisTemplate.opsForValue().get(key);
        //isNotBlank若字符串为"",null,"\t\n"会false，若为"abc"则为true；
        if (StrUtil.isNotBlank(json)) {
            //反序列化
            return JSONUtil.toBean(json, type);
        }
        //判断是否命中的缓存中的空数据（空数据是为了防止缓存穿透效果的一个策略）
        if (json != null) {
            return null;
        }
        //若为空，则去数据库中查找
        R r = dbFallback.apply(id);
        //若数据库中没有
        if (r == null) {
            //在缓存中写入有个空数据，防止缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //若数据库中有
        this.set(key, r, time, unit);
        return r;
    }

    //获取互斥锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    //释放互斥锁
    private boolean delete(String key){
        Boolean delete = stringRedisTemplate.delete(key);
        return BooleanUtil.isTrue(delete);

    }
}
