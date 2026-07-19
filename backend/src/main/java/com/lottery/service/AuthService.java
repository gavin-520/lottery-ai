package com.lottery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lottery.dto.LoginRequest;
import com.lottery.dto.LoginResponse;
import com.lottery.entity.SysUser;
import com.lottery.mapper.SysUserMapper;
import com.lottery.security.JwtTokenProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername())
        );
        if (user == null || user.getStatus() != 1 || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole());
        return new LoginResponse(token, user.getUsername(), user.getNickname(), user.getRole());
    }
}
