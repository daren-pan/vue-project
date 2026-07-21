package com.ruoyi.system.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.constant.Constants;
import com.ruoyi.common.core.event.LoginEvent;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.utils.ip.IpUtils;
import com.ruoyi.system.api.domain.SysLogininfor;
import com.ruoyi.system.api.domain.SysUser;
import com.ruoyi.system.service.ISysLogininforService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 登录事件消费者
 *
 * @author ruoyi
 */
@Component
public class LoginEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LoginEventConsumer.class);

    @Autowired
    private ISysLogininforService logininforService;

    @Autowired
    private ISysUserService userService;

    /**
     * 监听登录事件：写登录日志 + 更新用户登录信息
     */
    @RabbitListener(queues = "auth.login.queue")
    public void handleLoginEvent(LoginEvent event) {
        log.info("收到登录事件: username={}, type={}, ip={}",
                event.getUsername(), event.getEventType(), event.getIpaddr());

        try {
            // 1. 记录登录日志
            saveLoginLog(event);

            // 2. 更新用户登录 IP 和时间
            if (event.getUserId() != null) {
                updateUserLoginInfo(event.getUserId());
            }
        } catch (Exception e) {
            log.error("处理登录事件失败: {}", event, e);
            throw e; // 抛出异常触发重试，最终进死信队列
        }
    }

    private void saveLoginLog(LoginEvent event) {
        SysLogininfor logininfor = new SysLogininfor();
        logininfor.setUserName(event.getUsername());
        logininfor.setIpaddr(event.getIpaddr());
        logininfor.setMsg(event.getMessage());

        if (StringUtils.equalsAny(event.getEventType(),
                Constants.LOGIN_SUCCESS, Constants.LOGOUT, Constants.REGISTER)) {
            logininfor.setStatus(Constants.LOGIN_SUCCESS_STATUS);
        } else if (Constants.LOGIN_FAIL.equals(event.getEventType())) {
            logininfor.setStatus(Constants.LOGIN_FAIL_STATUS);
        }
        logininforService.insertLogininfor(logininfor);
        log.debug("登录日志已写入: {}", event.getUsername());
    }

    private void updateUserLoginInfo(Long userId) {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setLoginIp(IpUtils.getIpAddr());
        userService.updateById(sysUser);
        log.debug("用户登录信息已更新: userId={}", userId);
    }
}
