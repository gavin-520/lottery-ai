package com.lottery.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.entity.SysUser;
import com.lottery.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<>());
        if (count == 0) {
            createUser("admin", "admin123", "管理员", "ADMIN");
            createUser("analyst", "analyst123", "分析师", "ANALYST");
            createUser("user", "user123", "普通用户", "USER");
            log.info("Default users created: admin/admin123, analyst/analyst123, user/user123");
        }
    }

    private void createUser(String username, String password, String nickname, String role) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        user.setRole(role);
        user.setStatus(1);
        sysUserMapper.insert(user);
    }
}
