package com.alibaba.rocketmq.service;

import static com.alibaba.rocketmq.common.Tool.str;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.rocketmq.cache.Cache;
import com.alibaba.rocketmq.cache.CacheManager;
import com.alibaba.rocketmq.common.*;
import org.apache.commons.cli.Option;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.alibaba.rocketmq.common.admin.ConsumeStats;
import com.alibaba.rocketmq.common.admin.OffsetWrapper;
import com.alibaba.rocketmq.common.message.MessageQueue;
import com.alibaba.rocketmq.common.protocol.body.ConsumerConnection;
import com.alibaba.rocketmq.common.protocol.body.TopicList;
import com.alibaba.rocketmq.common.protocol.heartbeat.ConsumeType;
import com.alibaba.rocketmq.common.protocol.heartbeat.MessageModel;
import com.alibaba.rocketmq.common.subscription.SubscriptionGroupConfig;
import com.alibaba.rocketmq.tools.admin.DefaultMQAdminExt;
import com.alibaba.rocketmq.tools.command.CommandUtil;
import com.alibaba.rocketmq.tools.command.consumer.ConsumerProgressSubCommand;
import com.alibaba.rocketmq.tools.command.consumer.DeleteSubscriptionGroupCommand;
import com.alibaba.rocketmq.tools.command.consumer.UpdateSubGroupSubCommand;
import com.alibaba.rocketmq.validate.CmdTrace;
import sun.net.www.http.HttpClient;


/**
 * 
 * @author yankai913@gmail.com
 * @date 2014-2-18
 */
@Service
public class ConsumerService extends AbstractService {

    static final Logger logger = LoggerFactory.getLogger(ConsumerService.class);

    @Autowired
    private LogMailService logMailService;
    @Autowired
    private CacheManager cacheManager;

    static final ConsumerProgressSubCommand consumerProgressSubCommand = new ConsumerProgressSubCommand();


    public Collection<Option> getOptionsForConsumerProgress() {
        return getOptions(consumerProgressSubCommand);
    }

    /**
     * 主要用于邮箱发送
     * @param list
     */
    private void sendStackOverMail(List list) {
        String title = "RocketMQ消息堆积超出上限!";
        StringBuffer contentBuffer = new StringBuffer();
        contentBuffer.append("RocketMQ消息堆积超出上限（" + configureInitializer.getStackOverNum() + "），请及时处理！点击<a href='http://" + configureInitializer.getDomain() + "/consumer/consumerProgress.do'> 这里 </a> 进入查看<br/>");
        contentBuffer.append("<br/>堆积的消费者群组：").append("<br/>");
        for(int i=0;i<list.size();i++){
            Object[] tr = (Object[])list.get(i);
            contentBuffer.append("\t" + tr[0]);
            contentBuffer.append("\t总堆积数量：");
            contentBuffer.append(tr[6]);
            contentBuffer.append("\t<a href='http://" + configureInitializer.getDomain() + "/consumer/consumerProgress.do?groupName=" + tr[0] + "'> 查看 </a>");
            contentBuffer.append("<br/>");
        }
        //使用字符串的MD5值作为缓存键
        String content = contentBuffer.toString();
        String key = MD5Utils.md5(content);
        //判断重发次数，有效时间内超过发送次数不再重发
        Cache cache = cacheManager.getCacheInfo(key);
        if(cache != null && Integer.parseInt(cache.getValue().toString()) >= configureInitializer.getAlarmMaxSendTimesInOnePeriod()){
            logger.info("超出报警次数:{}，不发送报警信息，内容:{}", configureInitializer.getAlarmMaxSendTimesInOnePeriod(), content);
            return ;
        }
        Boolean result = logMailService.sendHtmlEmail(configureInitializer.getEmailReceiver(), title, contentBuffer.toString());
        if(result){
            //记录发送缓存
            if(cache == null){
                cache = cacheManager.putCacheInfo(key, 0, configureInitializer.getAlarmOnePeriodSeconds()*1000);
            }
            //缓存计数递增，此处会有并发问题，因为目前是单线程处理，暂且认为不会有并发问题
            Integer times = (Integer) cache.getValue() + 1;
            cache.setValue(times);
            cacheManager.putCache(key, cache);
            logger.info("消息堆积报警邮件发送成功");
        }
        else{
            logger.error("消息堆积报警邮件发送失败！！！");
        }
    }

    /**
     * 主要用于短信发送
     * @param overStackMap
     */
    private void sendStackOverMsg(Map<String,Long> overStackMap) {
        String title = "RocketMQ消息堆积报警:";
        StringBuffer contentBuffer = new StringBuffer();
        for(String key : overStackMap.keySet()){
            contentBuffer.append("\t" + key);
            contentBuffer.append("总堆积数量：");
            contentBuffer.append(overStackMap.get(key));

        }
        //使用字符串的MD5值作为缓存键
        String content = title+contentBuffer.toString();
        String phones[] = (configureInitializer.getSmsNotifyPhones()==null?"":configureInitializer.getSmsNotifyPhones()).split(",");
        if(phones.length>0){
            for(String phone : phones) {
                //发短信
                String url = configureInitializer.getSmsUrl();
                Map<String, String> paramMap = new HashMap<String, String>();
                paramMap.put("userName", configureInitializer.getSmsUserName());
                paramMap.put("passWord", configureInitializer.getSmsPassword());
                paramMap.put("phone", phone);
                paramMap.put("content", content);
                paramMap.put("channelId", configureInitializer.getSmsChannelid());
                try {
                    String result = HttpClientUtil.post(url, paramMap);
                    logger.info("【短信预警结果】====================>：" + result);
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("【短信预警失败】"+e.getMessage());
                }

            }
        }
    }

    /**
     * 每30分钟检测一次 堆积
     */
//    @Scheduled(fixedDelay = 30 * 60 * 1000)
    @Scheduled(fixedDelay = 10 * 1000)
    public void checkTopicStactOver(){
        logger.info("开始检测消息堆积....");
        long start = System.currentTimeMillis();
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
            Table table = consumerProgress(null);
            List list = new ArrayList();
            int size = table.getTbodyData().size();
            for (int i = 0; i < size; i++) {
                Object[] tr = table.getTbodyData().get(i);
                //记录超出积压阀值的消费组
                if ((StringUtils.isNotBlank((String) tr[1]) && Integer.parseInt(tr[1].toString()) > 0) && StringUtils.isNotBlank((String) tr[4]) && ((tr[6] != null && Integer.parseInt(tr[6].toString()) > configureInitializer.getStackOverNum()))) {
                    list.add(tr);
                }
            }
            for(int i =0 ; i < list.size(); i++){
                Object[] tr = (Object[])list.get(i);
                checkTopicByGroup(tr[0].toString());
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        finally {
            logger.info("消息堆积检测任务执行时长:{}ms", System.currentTimeMillis() - start);
        }

    }

    /**
     * 按组名查询堆积数量
     * @param groupName
     * @throws Throwable
     */
    private void checkTopicByGroup(String groupName) throws Throwable {
        // "#Topic",//
        // "#Broker Name",//
        // "#QID",//
        // "#Broker Offset",//
        // "#Consumer Offset",//
        // "#Diff" //
        Table table = consumerProgress(groupName);
        List list = new ArrayList();
        int size = table.getTbodyData().size();
        Map<String,Long> overStackMap = new ConcurrentHashMap<String, Long>();
        for (int i = 0; i < size; i++) {
            Object[] tr = table.getTbodyData().get(i);
            if(overStackMap.containsKey(tr[0])){
                overStackMap.put((String)tr[0],overStackMap.get(tr[0])+Long.parseLong((String)tr[5]));
            }else{
                overStackMap.put((String)tr[0],Long.parseLong((String)tr[5]));
            }
        }
        for(String key : overStackMap.keySet()){
            if(overStackMap.get(key) == 0){
                overStackMap.remove(key);
            }
        }
        if(overStackMap.size()>0){
            sendStackOverMsg(overStackMap);
        }
    }

    @CmdTrace(cmdClazz = ConsumerProgressSubCommand.class)
    public Table consumerProgress(String consumerGroup) throws Throwable {
        Throwable t = null;
        DefaultMQAdminExt defaultMQAdminExt = getDefaultMQAdminExt();
        try {
            defaultMQAdminExt.start();
            if (isNotBlank(consumerGroup)) {
                ConsumeStats consumeStats = defaultMQAdminExt.examineConsumeStats(consumerGroup);

                List<MessageQueue> mqList = new LinkedList<MessageQueue>();
                mqList.addAll(consumeStats.getOffsetTable().keySet());
                Collections.sort(mqList);
                // System.out.printf("%-32s  %-32s  %-4s  %-20s  %-20s  %s\n",//
                // "#Topic",//
                // "#Broker Name",//
                // "#QID",//
                // "#Broker Offset",//
                // "#Consumer Offset",//
                // "#Diff" //
                // );
                String[] thead =
                        new String[] { "#Topic", "#Broker Name", "#QID", "#Broker Offset",
                                      "#Consumer Offset", "#Diff" };
                long diffTotal = 0L;
                Table table = new Table(thead, mqList.size());
                for (MessageQueue mq : mqList) {
                    OffsetWrapper offsetWrapper = consumeStats.getOffsetTable().get(mq);

                    long diff = offsetWrapper.getBrokerOffset() - offsetWrapper.getConsumerOffset();
                    diffTotal += diff;

                    // System.out.printf("%-32s  %-32s  %-4d  %-20d  %-20d  %d\n",//
                    // UtilAll.frontStringAtLeast(mq.getTopic(), 32),//
                    // UtilAll.frontStringAtLeast(mq.getBrokerName(), 32),//
                    // mq.getQueueId(),//
                    // offsetWrapper.getBrokerOffset(),//
                    // offsetWrapper.getConsumerOffset(),//
                    // diff //
                    // );
                    Object[] tr = table.createTR();
                    tr[0] = mq.getTopic();//UtilAll.frontStringAtLeast(mq.getTopic(), 64);
                    tr[1] = mq.getBrokerName();//UtilAll.frontStringAtLeast(mq.getBrokerName(), 64);
                    tr[2] = str(mq.getQueueId());
                    tr[3] = str(offsetWrapper.getBrokerOffset());
                    tr[4] = str(offsetWrapper.getConsumerOffset());
                    tr[5] = str(diff);

                    table.insertTR(tr);
                }

                // System.out.println("");
                // System.out.printf("Consume TPS: %d\n",
                // consumeStats.getConsumeTps());
                // System.out.printf("Diff Total: %d\n", diffTotal);

                table.addExtData("Consume TPS:", str(consumeStats.getConsumeTps()));
                table.addExtData("Diff Total:", str(diffTotal));

                return table;
            }
            else {
                // System.out.printf("%-32s  %-6s  %-24s %-5s  %-14s  %-7s  %s\n",//
                // "#Group",//
                // "#Count",//
                // "#Version",//
                // "#Type",//
                // "#Model",//
                // "#TPS",//
                // "#Diff Total"//
                // );

                String[] thead =
                        new String[] { "#Group", "#Count", "#Version", "#Type", "#Model", "#TPS",
                                      "#Diff Total" };

                List<GroupConsumeInfo> groupConsumeInfoList = new LinkedList<GroupConsumeInfo>();
                TopicList topicList = defaultMQAdminExt.fetchAllTopicList();
                for (String topic : topicList.getTopicList()) {
                    if (topic.startsWith(MixAll.RETRY_GROUP_TOPIC_PREFIX)) {
                        String tconsumerGroup = topic.substring(MixAll.RETRY_GROUP_TOPIC_PREFIX.length());

                        try {
                            ConsumeStats consumeStats = null;
                            try {
                                consumeStats = defaultMQAdminExt.examineConsumeStats(tconsumerGroup);
                            } catch (Exception e) {
                                logger.warn("examineConsumeStats exception, " + tconsumerGroup, e);
                            }

                            ConsumerConnection cc = null;
                            try {
                                cc = defaultMQAdminExt.examineConsumerConnectionInfo(tconsumerGroup);
                            } catch (Exception e) {
                                logger.warn("examineConsumerConnectionInfo exception, " + tconsumerGroup, e);
                            }

                            GroupConsumeInfo groupConsumeInfo = new GroupConsumeInfo();
                            groupConsumeInfo.setGroup(tconsumerGroup);

                            if (consumeStats != null) {
                                groupConsumeInfo.setConsumeTps((int) consumeStats.getConsumeTps());
                                groupConsumeInfo.setDiffTotal(consumeStats.computeTotalDiff());
                            }

                            if (cc != null) {
                                groupConsumeInfo.setCount(cc.getConnectionSet().size());
                                groupConsumeInfo.setMessageModel(cc.getMessageModel());
                                groupConsumeInfo.setConsumeType(cc.getConsumeType());
                                groupConsumeInfo.setVersion(cc.computeMinVersion());
                            }

                            groupConsumeInfoList.add(groupConsumeInfo);
                        } catch (Exception e) {
                            logger.warn("examineConsumeStats or examineConsumerConnectionInfo exception, "
                                    + tconsumerGroup, e);
                        }
                    }
                }
                Collections.sort(groupConsumeInfoList);

                Table table = new Table(thead, groupConsumeInfoList.size());
                for (GroupConsumeInfo info : groupConsumeInfoList) {
                    // System.out.printf("%-32s  %-6d  %-24s %-5s  %-14s  %-7d  %d\n",//
                    // UtilAll.frontStringAtLeast(info.getGroup(),
                    // 32),//
                    // info.getCount(),//
                    // info.versionDesc(),//
                    // info.consumeTypeDesc(),//
                    // info.messageModelDesc(),//
                    // info.getConsumeTps(),//
                    // info.getDiffTotal()//
                    // );
                    Object[] tr = table.createTR();
                    tr[0] = UtilAll.frontStringAtLeast(info.getGroup(), 32);
                    tr[1] = str(info.getCount());
                    tr[2] = info.versionDesc();
                    tr[3] = info.consumeTypeDesc();
                    tr[4] = info.messageModelDesc();
                    tr[5] = str(info.getConsumeTps());
                    tr[6] = str(info.getDiffTotal());
                    table.insertTR(tr);
                }
                return table;
            }

        }
        catch (Throwable e) {
            logger.error(e.getMessage(), e);
            t = e;
        }
        finally {
            shutdownDefaultMQAdminExt(defaultMQAdminExt);
        }
        throw t;
    }

    static final DeleteSubscriptionGroupCommand deleteSubscriptionGroupCommand =
            new DeleteSubscriptionGroupCommand();


    public Collection<Option> getOptionsForDeleteSubGroup() {
        return getOptions(deleteSubscriptionGroupCommand);
    }


    @CmdTrace(cmdClazz = DeleteSubscriptionGroupCommand.class)
    public boolean deleteSubGroup(String groupName, String brokerAddr, String clusterName) throws Throwable {
        Throwable t = null;
        DefaultMQAdminExt adminExt = getDefaultMQAdminExt();
        try {
            if (isNotBlank(brokerAddr)) {
                adminExt.start();

                adminExt.deleteSubscriptionGroup(brokerAddr, groupName);
                // System.out.printf("delete subscription group [%s] from broker [%s] success.\n",
                // groupName,addr);

                return true;
            }
            else if (isNotBlank(clusterName)) {
                adminExt.start();

                Set<String> masterSet = CommandUtil.fetchMasterAddrByClusterName(adminExt, clusterName);
                for (String master : masterSet) {
                    adminExt.deleteSubscriptionGroup(master, groupName);
                    // System.out.printf(
                    // "delete subscription group [%s] from broker [%s] in cluster [%s] success.\n",
                    // groupName, master, clusterName);
                }
                return true;
            }
            else {
                throw new IllegalStateException("brokerAddr or clusterName can not be all blank");
            }
        }
        catch (Throwable e) {
            logger.error(e.getMessage(), e);
            t = e;
        }
        finally {
            shutdownDefaultMQAdminExt(adminExt);
        }
        throw t;
    }

    static final UpdateSubGroupSubCommand updateSubGroupSubCommand = new UpdateSubGroupSubCommand();


    public Collection<Option> getOptionsForUpdateSubGroup() {
        return getOptions(updateSubGroupSubCommand);
    }


    @CmdTrace(cmdClazz = UpdateSubGroupSubCommand.class)
    public boolean updateSubGroup(String brokerAddr, String clusterName, String groupName,
            String consumeEnable, String consumeFromMinEnable, String consumeBroadcastEnable,
            String retryQueueNums, String retryMaxTimes, String brokerId, String whichBrokerWhenConsumeSlowly)
            throws Throwable {
        Throwable t = null;
        DefaultMQAdminExt defaultMQAdminExt = getDefaultMQAdminExt();

        try {
            SubscriptionGroupConfig subscriptionGroupConfig = new SubscriptionGroupConfig();
            subscriptionGroupConfig.setConsumeBroadcastEnable(false);
            subscriptionGroupConfig.setConsumeFromMinEnable(false);

            // groupName
            subscriptionGroupConfig.setGroupName(groupName);

            // consumeEnable
            if (isNotBlank(consumeEnable)) {
                subscriptionGroupConfig.setConsumeEnable(Boolean.parseBoolean(consumeEnable.trim()));
            }

            // consumeFromMinEnable
            if (isNotBlank(consumeFromMinEnable)) {
                subscriptionGroupConfig.setConsumeFromMinEnable(Boolean.parseBoolean(consumeFromMinEnable
                    .trim()));
            }

            // consumeBroadcastEnable
            if (isNotBlank(consumeBroadcastEnable)) {
                subscriptionGroupConfig.setConsumeBroadcastEnable(Boolean.parseBoolean(consumeBroadcastEnable
                    .trim()));
            }

            // retryQueueNums
            if (isNotBlank(retryQueueNums)) {
                subscriptionGroupConfig.setRetryQueueNums(Integer.parseInt(retryQueueNums.trim()));
            }

            // retryMaxTimes
            if (isNotBlank(retryMaxTimes)) {
                subscriptionGroupConfig.setRetryMaxTimes(Integer.parseInt(retryMaxTimes.trim()));
            }

            // brokerId
            if (isNotBlank(brokerId)) {
                subscriptionGroupConfig.setBrokerId(Long.parseLong(brokerId.trim()));
            }

            // whichBrokerWhenConsumeSlowly
            if (isNotBlank(whichBrokerWhenConsumeSlowly)) {
                subscriptionGroupConfig.setWhichBrokerWhenConsumeSlowly(Long
                    .parseLong(whichBrokerWhenConsumeSlowly.trim()));
            }

            if (isNotBlank(brokerAddr)) {

                defaultMQAdminExt.start();

                defaultMQAdminExt.createAndUpdateSubscriptionGroupConfig(brokerAddr, subscriptionGroupConfig);
                // System.out.printf("create subscription group to %s success.\n",
                // addr);
                // System.out.println(subscriptionGroupConfig);
                return true;

            }
            else if (isNotBlank(clusterName)) {

                defaultMQAdminExt.start();

                Set<String> masterSet =
                        CommandUtil.fetchMasterAddrByClusterName(defaultMQAdminExt, clusterName);
                for (String addr : masterSet) {
                    defaultMQAdminExt.createAndUpdateSubscriptionGroupConfig(addr, subscriptionGroupConfig);
                    // System.out.printf("create subscription group to %s success.\n",
                    // addr);
                }
                // System.out.println(subscriptionGroupConfig);
                return true;
            }
            else {
                throw new IllegalStateException("brokerAddr or clusterName can not be all blank");
            }

        }
        catch (Throwable e) {
            logger.error(e.getMessage(), e);
            t = e;
        }
        finally {
            shutdownDefaultMQAdminExt(defaultMQAdminExt);
        }

        throw t;
    }
}


class GroupConsumeInfo implements Comparable<GroupConsumeInfo> {
    private String group;
    private int version;
    private int count;
    private ConsumeType consumeType;
    private MessageModel messageModel;
    private int consumeTps;
    private long diffTotal;


    public String getGroup() {
        return group;
    }


    public String consumeTypeDesc() {
        if (this.count != 0) {
            return this.getConsumeType() == ConsumeType.CONSUME_ACTIVELY ? "PULL" : "PUSH";
        }
        return "";
    }


    public String messageModelDesc() {
        if (this.count != 0 && this.getConsumeType() == ConsumeType.CONSUME_PASSIVELY) {
            return this.getMessageModel().toString();
        }
        return "";
    }


    public String versionDesc() {
        if (this.count != 0) {
            return MQVersion.getVersionDesc(this.version);
        }
        return "";
    }


    public void setGroup(String group) {
        this.group = group;
    }


    public int getCount() {
        return count;
    }


    public void setCount(int count) {
        this.count = count;
    }


    public ConsumeType getConsumeType() {
        return consumeType;
    }


    public void setConsumeType(ConsumeType consumeType) {
        this.consumeType = consumeType;
    }


    public MessageModel getMessageModel() {
        return messageModel;
    }


    public void setMessageModel(MessageModel messageModel) {
        this.messageModel = messageModel;
    }


    public long getDiffTotal() {
        return diffTotal;
    }


    public void setDiffTotal(long diffTotal) {
        this.diffTotal = diffTotal;
    }


    @Override
    public int compareTo(GroupConsumeInfo o) {
        if (this.count != o.count) {
            return o.count - this.count;
        }

        return (int) (o.diffTotal - diffTotal);
    }


    public int getConsumeTps() {
        return consumeTps;
    }


    public void setConsumeTps(int consumeTps) {
        this.consumeTps = consumeTps;
    }


    public int getVersion() {
        return version;
    }


    public void setVersion(int version) {
        this.version = version;
    }
}
