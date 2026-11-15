package com.bikeassembly.util;

import com.bikeassembly.context.BaseContext;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 用户上下文工具：统一获取当前用户 ID 和显示名。
 * 消除各 Controller 中重复的 getCurrentOperatorName() 方法。
 */
@Component
@RequiredArgsConstructor
public class UserContextHelper {

    private final SysUserMapper sysUserMapper;

    /**
     * 获取当前登录用户 ID。
     */
    public Long getCurrentUserId() {
        return BaseContext.getCurrentId();
    }

    /**
     * 获取当前登录用户的显示名（优先 nickname，其次 username）。
     */
    public String getCurrentUserDisplayName() {
        Long userId = getCurrentUserId();
        if (userId == null) return "未知用户";
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) return "未知用户";
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }
}
