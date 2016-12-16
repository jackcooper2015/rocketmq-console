package com.alibaba.rocketmq.cache;

/**
 * 缓存对象
 * @author yaojinwei<yjw0909@gmail.com>
 * @since 2016/9/23
 */
public class Cache {
        private String key;//缓存ID 
        private Object value;//缓存数据 
        private long timeOut;//过期时间
        private boolean expired; //是否终止 
        public Cache() { 
                super(); 
        } 
 
        public Cache(String key, Object value, long timeOut, boolean expired) { 
                this.key = key; 
                this.value = value; 
                this.timeOut = timeOut; 
                this.expired = expired; 
        } 
 
        public String getKey() { 
                return key; 
        } 
 
        public long getTimeOut() { 
                return timeOut; 
        } 
 
        public Object getValue() { 
                return value; 
        } 
 
        public void setKey(String string) { 
                key = string; 
        } 
 
        public void setTimeOut(long l) { 
                timeOut = l; 
        } 
 
        public void setValue(Object object) { 
                value = object; 
        } 
 
        public boolean isExpired() { 
                return expired; 
        } 
 
        public void setExpired(boolean b) { 
                expired = b; 
        } 
} 