package com.alibaba.rocketmq.task;

import com.alibaba.rocketmq.common.Table;
import com.alibaba.rocketmq.common.ThreadFactoryImpl;
import com.alibaba.rocketmq.common.protocol.body.TopicList;
import com.alibaba.rocketmq.service.ConsumerService;
import io.netty.channel.Channel;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author yaojinwei<yjw0909@gmail.com>
 * @since 2016/9/22
 */
public class ExamineTopicStackOverTask {
    private static final Logger log = LoggerFactory.getLogger(ExamineTopicStackOverTask.class);

    @Resource
    private ConsumerService consumerService;

    private ScheduledExecutorService checkTopicStackOverExecutorService = Executors
            .newSingleThreadScheduledExecutor(new ThreadFactoryImpl("CheckTopicStackOverThread"));

    /**
     * 定时检测堆积的消息
     */
    private void checkTopicStactOver(){
        //查询消费者列表
        try {
            // System.out.printf("%-32s  %-6s  %-24s %-5s  %-14s  %-7s  %s\n",//
            // "#Group",//
            // "#Count",//
            // "#Version",//
            // "#Type",//
            // "#Model",//
            // "#TPS",//
            // "#Diff Total"//
            // );
            Table table = consumerService.consumerProgress(null);
            int size  = table.getTbodyData().size();
            for(int i=0;i<size;i++){
                Object[] tr = table.getTbodyData().get(i);
                if( (tr[1] != null && (Integer)tr[1] > 0) && StringUtils.isNotBlank((String) tr[4]) && ( (tr[6] != null && (Integer)tr[6] > 100)) ){
                    //发邮件

                }
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public void checkTopicStackOverExecutor() {
        this.checkTopicStackOverExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    //查询消费者列表
                    checkTopicStactOver();
                } catch (Throwable e) {

                }
            }
        }, 1000 * 10, 1000 * 30, TimeUnit.MILLISECONDS);
    }
}
