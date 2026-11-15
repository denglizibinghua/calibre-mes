package com.bikeassembly.service;

import com.bikeassembly.constant.PermissionConstant;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.event.RoleMenusChangedEvent;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.utils.RedisService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 权限服务：权限判断、数据范围、权限缓存管理。
 *
 * JWT 只存 userId，权限每次请求通过本服务从 DB 加载。
 * 使用 Caffeine 本地缓存，TTL 30 分钟。
 * 角色/权限变更时主动清除缓存。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PermissionService {

    private final SysRoleMenuService sysRoleMenuService;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserMapper sysUserMapper;
    private final RedisService redisService;

    /** 二级缓存 TTL（与 Caffeine 一致，30 分钟） */
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    /** Redis 权限缓存 key 前缀 */
    private static final String PERM_KEY = "perm:";
    /** Redis 数据范围缓存 key 前缀 */
    private static final String DATASCOPE_KEY = "datascope:";

    /** 用户权限缓存：userId → Set<permissionCode> */
    private Cache<Long, Set<String>> permissionCache;

    /** 用户数据范围缓存：userId → dataScope */
    private Cache<Long, String> dataScopeCache;

    @PostConstruct
    public void init() {
        permissionCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(5000)
                .build();
        dataScopeCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .maximumSize(5000)
                .build();
    }

    // ==================== 权限判断 ====================

    /**
     * 判断用户是否拥有指定权限。
     * 管理员（role_key=admin）自动拥有全部权限。
     */
    public boolean hasPermission(Long userId, String permissionCode) {
        if (userId == null || permissionCode == null) return false;
        Set<String> perms = getUserPermissions(userId);
        // 管理员通配
        if (perms.contains(PermissionConstant.WILDCARD)) return true;
        return perms.contains(permissionCode);
    }

    /**
     * 获取用户的所有权限编码（含缓存）。
     * 管理员自动返回通配权限。
     */
    public Set<String> getUserPermissions(Long userId) {
        if (userId == null) return Collections.emptySet();

        Set<String> cached = permissionCache.getIfPresent(userId);
        if (cached != null) return cached;

        // 二级缓存：Redis（多实例共享），命中后回填本地 Caffeine
        List<String> redisPerms = redisService.get(PERM_KEY + userId, List.class);
        if (redisPerms != null) {
            Set<String> permSet = new HashSet<>(redisPerms);
            permissionCache.put(userId, permSet);
            return permSet;
        }

        // 判断是否为管理员
        SysUser user = sysUserMapper.selectById(userId);
        if (user != null && user.getRoleId() != null) {
            SysRole role = sysRoleMapper.selectById(user.getRoleId());
            if (role != null && "admin".equals(role.getRoleKey())) {
                Set<String> adminPerms = new HashSet<>();
                adminPerms.add(PermissionConstant.WILDCARD);
                permissionCache.put(userId, adminPerms);
                redisService.set(PERM_KEY + userId, new ArrayList<>(adminPerms), CACHE_TTL);
                return adminPerms;
            }
        }

        // 从 DB 加载
        List<String> perms = sysRoleMenuService.getPermsByUserId(userId);
        Set<String> permSet = new HashSet<>(perms);
        permissionCache.put(userId, permSet);
        redisService.set(PERM_KEY + userId, new ArrayList<>(permSet), CACHE_TTL);
        return permSet;
    }

    // ==================== 数据范围 ====================

    /**
     * 获取用户的数据范围（含缓存）。
     */
    public String getUserDataScope(Long userId) {
        if (userId == null) return "SELF";

        String cached = dataScopeCache.getIfPresent(userId);
        if (cached != null) return cached;

        // 二级缓存：Redis（多实例共享）
        String redisScope = redisService.get(DATASCOPE_KEY + userId, String.class);
        if (redisScope != null) {
            dataScopeCache.put(userId, redisScope);
            return redisScope;
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getRoleId() == null) {
            dataScopeCache.put(userId, "SELF");
            redisService.set(DATASCOPE_KEY + userId, "SELF", CACHE_TTL);
            return "SELF";
        }

        SysRole role = sysRoleMapper.selectById(user.getRoleId());
        String dataScope = (role != null && role.getDataScope() != null) ? role.getDataScope() : "SELF";
        dataScopeCache.put(userId, dataScope);
        redisService.set(DATASCOPE_KEY + userId, dataScope, CACHE_TTL);
        return dataScope;
    }

    /**
     * 判断用户是否拥有全部数据范围权限。
     */
    public boolean hasAllDataScope(Long userId) {
        return "ALL".equals(getUserDataScope(userId));
    }

    // ==================== 缓存管理 ====================

    /**
     * 清除指定用户的权限缓存。
     */
    public void evictUserCache(Long userId) {
        if (userId != null) {
            permissionCache.invalidate(userId);
            dataScopeCache.invalidate(userId);
            redisService.delete(PERM_KEY + userId);
            redisService.delete(DATASCOPE_KEY + userId);
            log.debug("已清除用户权限缓存：userId={}", userId);
        }
    }

    /**
     * 清除属于某角色的所有用户的权限缓存。
     * 角色权限变更后调用。
     */
    public void evictRoleCache(Long roleId) {
        // 简单策略：清除全量缓存。
        // 生产环境可优化为精准清除，第一版先全量清除以保证正确性。
        permissionCache.invalidateAll();
        dataScopeCache.invalidateAll();
        redisService.deleteByPrefix(PERM_KEY);
        redisService.deleteByPrefix(DATASCOPE_KEY);
        log.info("已清除全部权限缓存（角色变更：roleId={}）", roleId);
    }

    /**
     * 监听角色菜单变更事件，自动清除权限缓存。
     * 解耦 SysRoleMenuServiceImpl 与 PermissionService 之间的循环依赖。
     */
    @EventListener
    public void onRoleMenusChanged(RoleMenusChangedEvent event) {
        if (event.getRoleId() != null) {
            evictRoleCache(event.getRoleId());
        }
    }

    /**
     * 清除全部权限缓存。
     */
    public void evictAllCache() {
        permissionCache.invalidateAll();
        dataScopeCache.invalidateAll();
        redisService.deleteByPrefix(PERM_KEY);
        redisService.deleteByPrefix(DATASCOPE_KEY);
        log.info("已清除全部权限缓存");
    }
}
