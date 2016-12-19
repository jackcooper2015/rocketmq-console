package com.alibaba.rocketmq.config;


import com.alibaba.rocketmq.common.MixAll;


/**
 * 把需要补充的初始化环境变量正式的放入系统属性中
 * 
 * @author yankai913@gmail.com
 * @date 2014-2-10
 * 
 */
public class ConfigureInitializer {
    
    private String namesrvAddr;

    private String domain;

    private String emailHost;

    private String emailPassword;

    private String emailUser;

    private String emailPort;

    private String fromEmail;

    private String emailReceiver;

    private boolean emailMonitorOn = false;

    private Integer stackOverNum = 100;

    private Integer alarmOnePeriodSeconds =14400;
    private Integer alarmMaxSendTimesInOnePeriod =3;

    private String smsUrl;

    private String smsUserName;

    private String smsPassword;

    private String smsNotifyPhones;

    private String smsChannelid;

    private String monitorGroupNames;

    public String getTopicOvernums() {
        return topicOvernums;
    }

    public void setTopicOvernums(String topicOvernums) {
        this.topicOvernums = topicOvernums;
    }

    private String topicOvernums;

    public String getSmsUrl() {
        return smsUrl;
    }

    public void setSmsUrl(String smsUrl) {
        this.smsUrl = smsUrl;
    }

    public String getSmsUserName() {
        return smsUserName;
    }

    public void setSmsUserName(String smsUserName) {
        this.smsUserName = smsUserName;
    }

    public String getSmsPassword() {
        return smsPassword;
    }

    public void setSmsPassword(String smsPassword) {
        this.smsPassword = smsPassword;
    }

    public String getSmsNotifyPhones() {
        return smsNotifyPhones;
    }

    public void setSmsNotifyPhones(String smsNotifyPhones) {
        this.smsNotifyPhones = smsNotifyPhones;
    }

    public String getSmsChannelid() {
        return smsChannelid;
    }

    public void setSmsChannelid(String smsChannelid) {
        this.smsChannelid = smsChannelid;
    }

    public String getMonitorGroupNames() {
        return monitorGroupNames;
    }

    public void setMonitorGroupNames(String monitorGroupNames) {
        this.monitorGroupNames = monitorGroupNames;
    }

    public Integer getAlarmOnePeriodSeconds() {
        return alarmOnePeriodSeconds;
    }

    public void setAlarmOnePeriodSeconds(Integer alarmOnePeriodSeconds) {
        this.alarmOnePeriodSeconds = alarmOnePeriodSeconds;
    }

    public Integer getAlarmMaxSendTimesInOnePeriod() {
        return alarmMaxSendTimesInOnePeriod;
    }

    public void setAlarmMaxSendTimesInOnePeriod(Integer alarmMaxSendTimesInOnePeriod) {
        this.alarmMaxSendTimesInOnePeriod = alarmMaxSendTimesInOnePeriod;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Integer getStackOverNum() {
        return stackOverNum;
    }

    public void setStackOverNum(Integer stackOverNum) {
        this.stackOverNum = stackOverNum;
    }

    public String getEmailHost() {
        return emailHost;
    }

    public void setEmailHost(String emailHost) {
        this.emailHost = emailHost;
    }

    public String getEmailPassword() {
        return emailPassword;
    }

    public void setEmailPassword(String emailPassword) {
        this.emailPassword = emailPassword;
    }

    public String getEmailUser() {
        return emailUser;
    }

    public void setEmailUser(String emailUser) {
        this.emailUser = emailUser;
    }

    public String getEmailPort() {
        return emailPort;
    }

    public void setEmailPort(String emailPort) {
        this.emailPort = emailPort;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getEmailReceiver() {
        return emailReceiver;
    }

    public void setEmailReceiver(String emailReceiver) {
        this.emailReceiver = emailReceiver;
    }

    public boolean isEmailMonitorOn() {
        return emailMonitorOn;
    }

    public void setEmailMonitorOn(boolean emailMonitorOn) {
        this.emailMonitorOn = emailMonitorOn;
    }

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public void init() {
        System.setProperty(MixAll.NAMESRV_ADDR_PROPERTY, namesrvAddr);
    }
}
