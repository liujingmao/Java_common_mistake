package com.cmb.javasum.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * 使用了 ConcurrentHashMap，不代表对它的多个操作之间的状态是一致的，是没有其他线程在操作它的，如果需要确保需要手动加锁。
 * 诸如 size、isEmpty 和 containsValue 等聚合方法，在并发情况下可能会反映 ConcurrentHashMap 的中间状态。
 * 因此在并发情况下，这些方法的返回值只能用作参考，而不能用于流程控制。显然，利用 size 方法计算差异值，是一个流程控制。
 * 诸如 putAll 这样的聚合方法也不能确保原子性，在 putAll 的过程中去获取数据可能会获取到部分数据。
 */

@RestController
@RequestMapping("concurrenthashmapmisuse")
@Slf4j
public class ConcurrentHashMapMisuseController {
    // 线程个数
    private static int THREAD_COUNT=10;
    // 总元素个数
    private static int ITEM_COUNT=1000;

    private ConcurrentHashMap<String,Long> getData(int count){
        return LongStream.rangeClosed(1,count)
                .boxed()
                .collect(Collectors.toConcurrentMap(i-> UUID.randomUUID()
                                .toString(), Function.identity(),(t1,t2)->t1,ConcurrentHashMap::new));
    }

    @GetMapping ("wrong")
    public String wrong() throws InterruptedException {
        //初始900个元素
        ConcurrentHashMap<String,Long> concurrentHashMap = getData(ITEM_COUNT-100);
        log.info("Init Size:{}",concurrentHashMap.size());


        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);

        // 线程池并发处理逻辑
        forkJoinPool.execute(()->IntStream
                .rangeClosed(1,10)
                .parallel()
                .forEach(a->{
                    //查询还需要补充多少个元素
                    int gap = ITEM_COUNT - concurrentHashMap.size();
                    log.info("Gap size:{}",gap);
                    //补充元素
                    concurrentHashMap.putAll(getData(gap));
                }));
        //等待所有任务完成
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1, TimeUnit.HOURS);
        //最终元素个数会是1000吗？
        log.info("Finished size:{}",concurrentHashMap.size());

        String result="";
        if (concurrentHashMap.size()==ITEM_COUNT)
        {
            result = "ok";
        } else {
            return "Erroe";
        }

        return result;
    }

    @GetMapping("right")
    public String right() throws InterruptedException {

        ConcurrentHashMap<String,Long> concurrentHashMap = getData(ITEM_COUNT - 100);
        log.info("Init Size:{}",concurrentHashMap);

        ForkJoinPool forkJoinPool = new ForkJoinPool(THREAD_COUNT);

        forkJoinPool.execute(()->IntStream.rangeClosed(1,10)
                .parallel()
                .forEach(b->{
                    synchronized (concurrentHashMap){
                        int gap = ITEM_COUNT - concurrentHashMap.size();
                        log.info("gap size:{}",gap);
                        concurrentHashMap.putAll(getData(gap));
                    }
                }));

        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(1,TimeUnit.HOURS);
        log.info("Finished Size:{}",concurrentHashMap.size());
        return "ok";
    }

}
