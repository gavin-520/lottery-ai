package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.dto.CreateUserRequest;
import com.lottery.dto.UserSummary;
import com.lottery.entity.SysUser;
import com.lottery.mapper.SysUserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class UserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummary> listUsers() {
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId))
                .stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername(), u.getNickname(), u.getRole(), u.getStatus(), u.getCreatedAt()))
                .toList();
    }

    public UserSummary createUser(CreateUserRequest request) {
        Long exists = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername())
        );
        if (exists > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole(request.getRole());
        user.setStatus(request.getStatus());
        sysUserMapper.insert(user);

        return new UserSummary(user.getId(), user.getUsername(), user.getNickname(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
