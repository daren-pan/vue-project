package com.ruoyi.system.mapper;

import com.ruoyi.system.RuoYiSystemApplication;
import com.ruoyi.system.api.domain.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = RuoYiSystemApplication.class)   // ① 显式指定启动类
public class UserMapperTest {
    @Autowired
    private SysUserMapper sysUserMapper;                  // ② 自动注入

    @Test
    public void testSelectById() {
        // 确保数据库中有 id=1 的记录，或者使用一个存在的 id
        SysUser user = sysUserMapper.selectById(1L);
        System.out.println(user);
    }
    @Test
    public void testSelectAll() {
        System.out.println("测试通过");
    }
}
