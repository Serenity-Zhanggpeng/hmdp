package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleDistributedLockBasedOnRedis;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private IVoucherOrderService iVoucherOrderService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Transactional       //原子性问题   几个操作执行 要么都超过要么都失败 有一个失败就都失败
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 根据 优惠券 id 查询数据库
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 2. 判断秒杀是否开始或结束（未开始或已结束，返回异常结果）
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始..");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("秒杀已经结束..");
        }

        //3,4乐观锁解决超卖问题   此处不会超卖：基于数据库的 update 语句自带行锁，一旦某个用户对某行进行 update 操作，
        // 其他用户只能查询但不能 update 被加锁的数据行。
        //只需要让 stock > 0 即可
        // 3. 判断库存是否充足（不充足返回异常结果）
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足..");
        }


 /*       // 6. 返回 订单 id  由优惠卷id创建订单 并返回订单id
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            //获取代理对象  知识点 spring框架事务失效 aop代理对象 synchronized锁对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);  //这里锁讲的非常好，看讲解！！！！！！！！！！
//            return this.createVoucherOrder(voucherId);
        }
*/
        //分布式锁
        Long userId = UserHolder.getUser().getId();
        // 创建锁对象并获取锁，判断是否获取锁成功
        SimpleDistributedLockBasedOnRedis lock = new SimpleDistributedLockBasedOnRedis("order:" + userId, stringRedisTemplate);
        redissonClient.getLock("lock:order:"+userId);   //api直接获取锁

        boolean isLocked = lock.tryLock(1200);
        //只有获取到了锁 说明线程x第一个走到这，才会在数据库中插入订单信息，表明已经抢到订单  之后在释放锁
        //这里是分布式锁解决的是多个线程并行执行，从而造成数据库插入多个相同的订单 一人多单的情况 所以要加锁
        if (!isLocked) {
            return Result.fail("不可重复下单！一人只能下一单");
        }
        try {
            // 获取代理对象  这里有点不懂
            IVoucherOrderService currentProxy = (IVoucherOrderService) AopContext.currentProxy();
            return currentProxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unlock();
        }
    }




    //这里锁讲的非常好，有关锁定使用，锁的对象锁的范围，看讲解！！！！！！！！！！
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        // 一人一单（根据 优惠券id 和 用户id 查询订单查询数据库，若果纯在，说明订单号已经有了，下过单了）
        Long userId = UserHolder.getUser().getId();
        Integer count = this.query().eq("voucher_id", voucherId).eq("user_id", userId).count();
        if (count > 0) {
            return Result.fail("不可重复下单！");
        }

        // 4. 减扣库存
        boolean isAccomplished = seckillVoucherService.update()
                // SET stock= stock - 1
                .setSql("stock = stock - 1")
                // WHERE  voucher_id = ? AND stock > 0
                .eq("voucher_id", voucherId).gt("stock", 0)
                .update();
        if (!isAccomplished) {
            return Result.fail("库存不足..");
        }


        // 5. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order"); //构建id号
        userId = UserHolder.getUser().getId();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        boolean isSaved = this.save(voucherOrder);
        if (!isSaved) {
            return Result.fail("下单失败..");
        }
        return Result.ok(orderId);  //返回订单id
    }

}
