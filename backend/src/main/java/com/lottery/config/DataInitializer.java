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
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("管理员");
            admin.setRole("ADMIN");
            admin.setStatus(1);
            sysUserMapper.insert(admin);
            log.info("Default admin user created (username: admin, password: admin123)");
        }
    }
}
