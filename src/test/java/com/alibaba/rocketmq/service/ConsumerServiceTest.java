package com.alibaba.rocketmq.service;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:spring/applicationContext.xml")
public class ConsumerServiceTest {
    @Autowired
    private ConsumerService consumerService;

    @Test
    public void testCheckTopicStactOver(){
        consumerService.checkTopicStactOver();
    }
}