package com.ruoyi.common.core.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录事件
 *
 * @author ruoyi
 */
public class LoginEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型：LOGIN_SUCCESS / LOGIN_FAIL / LOGOUT / REGISTER */
    private String eventType;

    /** 用户名 */
    private String username;

    /** 登录 IP */
    private String ipaddr;

    /** 消息内容 */
    private String message;

    /** 事件时间 */
    private LocalDateTime eventTime;

    /** 用户 ID（登录成功时有值） */
    private Long userId;

    public LoginEvent() {}

    public LoginEvent(String eventType, String username, String ipaddr, String message) {
        this.eventType = eventType;
        this.username = username;
        this.ipaddr = ipaddr;
        this.message = message;
        this.eventTime = LocalDateTime.now();
    }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getIpaddr() { return ipaddr; }
    public void setIpaddr(String ipaddr) { this.ipaddr = ipaddr; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    @Override
    public String toString() {
        return "LoginEvent{" +
                "eventType='" + eventType + '\'' +
                ", username='" + username + '\'' +
                ", ipaddr='" + ipaddr + '\'' +
                ", message='" + message + '\'' +
                ", eventTime=" + eventTime +
                ", userId=" + userId +
                '}';
    }
}
