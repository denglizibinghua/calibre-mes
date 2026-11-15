package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.SysUserDTO;
import com.bikeassembly.dto.UserLoginDTO;
import com.bikeassembly.dto.WxBindDTO;
import com.bikeassembly.dto.WxLoginDTO;
import com.bikeassembly.entity.SysRole;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.entity.SysUserPasswordHistory;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.mapper.SysRoleMapper;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.SysUserMapper;
import com.bikeassembly.service.PermissionService;
import com.bikeassembly.service.RefreshTokenService;
import com.bikeassembly.service.SysConfigService;
import com.bikeassembly.service.SysUserService;
import com.bikeassembly.service.WechatService;
import com.bikeassembly.utils.JwtUtil;
import com.bikeassembly.vo.SysUserVO;
import com.bikeassembly.vo.UserLoginVO;
import com.bikeassembly.vo.WxBindVO;
import com.bikeassembly.vo.WxLoginVO;
import com.bikeassembly.utils.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Slf4j
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private WechatService wechatService;
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private com.bikeassembly.mapper.SysUserPasswordHistoryMapper passwordHistoryMapper;
    @Autowired
    private com.bikeassembly.mapper.SysConfigMapper sysConfigMapper;
    @Autowired
    private LoginFailService loginFailService;
    @Autowired
    private SysConfigService sysConfigService;

    // ========== 登录逻辑 ==========

    /** BCrypt 密码编码器，用于密码加密和校验 */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    /** [业务规则] 连续登录失败最大次数，超过则锁定账号 */
    private static final int MAX_FAIL_COUNT = 5;
    /** [业务规则] 账号锁定时间（分钟），15 分钟后自动解锁 */
    private static final int LOCK_MINUTES = 15;
    /** [业务规则] 密码历史保留条数，用于防止密码重复使用 */
    private static final int PASSWORD_HISTORY_COUNT = 5;

    /**
     * [答辩入口] 管理端用户名密码登录。
     * [事务] 整个登录流程在一个事务中执行，rollbackFor = Exception.class
     * [核心业务] 登录判断顺序（必须按此顺序）：
     *   1. 查询用户 → 2. 检查锁定 → 3. BCrypt 密码校验 → 4. 检查禁用 → 5. 重置失败计数 → 6. 签发双 Token → 7. 返回权限
     * [调用链] Controller → SysUserServiceImpl.login → SysUserMapper.findByUsername → BCrypt → JwtUtil.createAdminAccessToken/createAdminRefreshToken
     * [数据库操作] 查询 sys_user（findByUsername），更新 sys_user（重置 failCount + lockedUntil + lastLoginTime），
     *   插入 user_refresh_token（saveRefreshToken），查询 sys_role_menu（加载权限）
     * [状态变化] loginFailCount → 0, lockedUntil → null, lastLoginTime → 当前时间
     * [异常处理] 账号不存在 → ACCOUNT_NOT_FOUND，密码错误 → PASSWORD_ERROR（同时递增失败计数），账号禁用 → ACCOUNT_DISABLED
     * [并发控制] 未发现锁或版本号，仅通过普通 updateById 更新登录状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginVO login(UserLoginDTO dto) {
        // [业务规则] 1. 根据用户名查询用户（使用 XML 中明确列出所有列的 findByUsername）
        SysUser user = sysUserMapper.findByUsername(dto.getUsername());
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // [业务规则] 2. 检查账号是否被锁定（Redis 锁定标记）
        if (loginFailService.isLocked(dto.getUsername())) {
            throw new BaseException("账号已被锁定，请" + LOCK_MINUTES + "分钟后再试");
        }

        // [业务规则] 3. BCrypt 密码校验：使用 BCryptPasswordEncoder.matches 比对密文
        if (!ENCODER.matches(dto.getPassword(), user.getPassword())) {
            // [安全] 登录失败计数使用 Redis 原子自增，达到阈值自动锁定
            loginFailService.incrementFailCount(dto.getUsername());
            throw new BaseException(MessageConstant.PASSWORD_ERROR);
        }

        // [业务规则] 4. 账号状态检查：status = "1" 表示已禁用，status = "0" 表示正常
        if ("1".equals(user.getStatus())) {
            throw new BaseException(MessageConstant.ACCOUNT_DISABLED);
        }

        // [状态变化] 5. 登录成功：重置连续失败次数为 0，清除锁定截止时间，记录最后登录时间
        SysUser successUpdate = new SysUser();
        successUpdate.setUserId(user.getUserId());
        successUpdate.setLoginFailCount(0);
        successUpdate.setLockedUntil(null);
        successUpdate.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(successUpdate);
        loginFailService.resetFail(dto.getUsername());

        // [业务规则] 6. 判断密码是否已过期（根据 passwordExpireAt 字段和 password.expire_days 配置）
        boolean passwordExpired = isPasswordExpired(user);

        // [Token] 7. 生成双 Token：Access Token（短有效期）+ Refresh Token（长有效期）
        // JWT 签名使用 admin 专属密钥（与 app 端密钥不同，实现双通道隔离）
        String accessToken = jwtUtil.createAdminAccessToken(user.getUserId(), user.getUsername());
        String refreshToken = jwtUtil.createAdminRefreshToken(user.getUserId(), user.getUsername());

        // [Token] 8. 将 Refresh Token 的 SHA-256 哈希存入 user_refresh_token 表
        // 数据库只存哈希不存明文，防止数据库泄露导致 Token 被盗用
        refreshTokenService.saveRefreshToken(user.getUserId(), JwtUtil.CLIENT_TYPE_ADMIN,
                jwtUtil.getTokenJti(refreshToken), refreshToken,
                jwtUtil.getAdminRefreshTtl(), null, null);

        // [权限控制] 9. 加载用户权限码列表和数据范围，随登录响应一起返回给前端
        List<String> permissions = new ArrayList<>(permissionService.getUserPermissions(user.getUserId()));
        String dataScope = permissionService.getUserDataScope(user.getUserId());

        return UserLoginVO.builder()
                .id(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roleId(user.getRoleId())
                .token(accessToken)        // @deprecated 保留字段，内容等同于 accessToken
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAdminAccessTtl() / 1000)        // 毫秒转秒
                .refreshExpiresIn(jwtUtil.getAdminRefreshTtl() / 1000)
                .mustChangePassword(user.getMustChangePassword())     // 1=首次登录/管理员重置后必须改密
                .passwordExpired(passwordExpired)
                .permissions(permissions)    // 权限码列表，如 ["system:user:list", "pro:workOrder:add"]
                .dataScope(dataScope)        // 数据范围：ALL / DEPT / SELF
                .build();
    }

    // ========== App 端用户名密码登录 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginVO appLogin(UserLoginDTO dto) {
        // 1. 根据用户名查询用户
        SysUser user = sysUserMapper.findByUsername(dto.getUsername());
        if (user == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        // 2. 检查账号是否被锁定（Redis 锁定标记）
        if (loginFailService.isLocked(dto.getUsername())) {
            throw new BaseException("账号已被锁定，请" + LOCK_MINUTES + "分钟后再试");
        }

        // 3. 密码校验
        if (!ENCODER.matches(dto.getPassword(), user.getPassword())) {
            loginFailService.incrementFailCount(dto.getUsername());
            throw new BaseException(MessageConstant.PASSWORD_ERROR);
        }

        // 4. 账号状态检查
        if ("1".equals(user.getStatus())) {
            throw new BaseException(MessageConstant.ACCOUNT_DISABLED);
        }

        // 5. 登录成功：重置失败计数 + 更新登录信息
        SysUser successUpdate = new SysUser();
        successUpdate.setUserId(user.getUserId());
        successUpdate.setLoginFailCount(0);
        successUpdate.setLockedUntil(null);
        successUpdate.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(successUpdate);
        loginFailService.resetFail(dto.getUsername());

        // 6. 判断密码过期
        boolean passwordExpired = isPasswordExpired(user);

        // 7. 生成 App 端双 Token（区别于 admin 端的 admin key 签名）
        String accessToken = jwtUtil.createAppAccessToken(user.getUserId(), user.getUsername());
        String refreshToken = jwtUtil.createAppRefreshToken(user.getUserId(), user.getUsername());

        // 8. 持久化 Refresh Token（客户端类型为 app）
        refreshTokenService.saveRefreshToken(user.getUserId(), JwtUtil.CLIENT_TYPE_APP,
                jwtUtil.getTokenJti(refreshToken), refreshToken,
                jwtUtil.getAppRefreshTtl(), null, null);

        // 9. 返回 UserLoginVO（含权限码和数据范围）
        List<String> permissions = new ArrayList<>(permissionService.getUserPermissions(user.getUserId()));
        String dataScope = permissionService.getUserDataScope(user.getUserId());

        return UserLoginVO.builder()
                .id(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roleId(user.getRoleId())
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAppAccessTtl() / 1000)
                .refreshExpiresIn(jwtUtil.getAppRefreshTtl() / 1000)
                .mustChangePassword(user.getMustChangePassword())
                .passwordExpired(passwordExpired)
                .permissions(permissions)
                .dataScope(dataScope)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginVO faceLogin(Long userId, String clientType) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        if ("1".equals(user.getStatus())) throw new BaseException(MessageConstant.ACCOUNT_DISABLED);
        boolean app = JwtUtil.CLIENT_TYPE_APP.equals(clientType);
        String accessToken = app
                ? jwtUtil.createAppAccessToken(user.getUserId(), user.getUsername())
                : jwtUtil.createAdminAccessToken(user.getUserId(), user.getUsername());
        String refreshToken = app
                ? jwtUtil.createAppRefreshToken(user.getUserId(), user.getUsername())
                : jwtUtil.createAdminRefreshToken(user.getUserId(), user.getUsername());
        refreshTokenService.saveRefreshToken(user.getUserId(), clientType,
                jwtUtil.getTokenJti(refreshToken), refreshToken,
                app ? jwtUtil.getAppRefreshTtl() : jwtUtil.getAdminRefreshTtl(), null, null);
        SysUser update = new SysUser();
        update.setUserId(user.getUserId());
        update.setLoginFailCount(0);
        update.setLockedUntil(null);
        update.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(update);
        loginFailService.resetFail(user.getUsername());
        List<String> permissions = new ArrayList<>(permissionService.getUserPermissions(user.getUserId()));
        return UserLoginVO.builder()
                .id(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roleId(user.getRoleId())
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn((app ? jwtUtil.getAppAccessTtl() : jwtUtil.getAdminAccessTtl()) / 1000)
                .refreshExpiresIn((app ? jwtUtil.getAppRefreshTtl() : jwtUtil.getAdminRefreshTtl()) / 1000)
                .mustChangePassword(user.getMustChangePassword())
                .passwordExpired(isPasswordExpired(user))
                .permissions(permissions)
                .dataScope(permissionService.getUserDataScope(user.getUserId()))
                .build();
    }

    // ========== 微信登录与已有账号绑定 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WxLoginVO wxLogin(WxLoginDTO dto) {
        String openid = wechatService.code2Session(dto.getCode());
        SysUser user = sysUserMapper.findByOpenid(openid);
        if (user == null) {
            // 仅创建无角色、无业务会话的绑定占位记录，完成绑定后删除。
            user = new SysUser();
            user.setUsername("wx_" + UUID.randomUUID().toString().replace("-", ""));
            user.setOpenid(openid);
            user.setNickname("待绑定微信");
            user.setPassword(ENCODER.encode(UUID.randomUUID().toString()));
            user.setStatus("0");
            user.setMustChangePassword(0);
            sysUserMapper.insert(user);
        }

        if ("1".equals(user.getStatus())) {
            throw new BaseException(MessageConstant.ACCOUNT_DISABLED);
        }
        if (isWechatPlaceholder(user)) {
            return WxLoginVO.builder()
                    .status(3)
                    .userId(user.getUserId())
                    .needBind(true)
                    .tempToken(jwtUtil.createTempToken(user.getUserId()))
                    .availableRoles(List.of())
                    .build();
        }
        if (loginFailService.isLocked(user.getUsername())) {
            throw new BaseException("账号已被锁定，请" + LOCK_MINUTES + "分钟后再试");
        }
        requireWechatAccountRole(user);

        SysUser loginUpdate = new SysUser();
        loginUpdate.setUserId(user.getUserId());
        loginUpdate.setLoginFailCount(0);
        loginUpdate.setLastLoginTime(LocalDateTime.now());
        sysUserMapper.updateById(loginUpdate);
        loginFailService.resetFail(user.getUsername());

        String accessToken = jwtUtil.createAppAccessToken(user.getUserId(), user.getUsername());
        String refreshToken = jwtUtil.createAppRefreshToken(user.getUserId(), user.getUsername());
        refreshTokenService.saveRefreshToken(user.getUserId(), JwtUtil.CLIENT_TYPE_APP,
                jwtUtil.getTokenJti(refreshToken), refreshToken, jwtUtil.getAppRefreshTtl(), null, null);
        List<String> permissions = new ArrayList<>(permissionService.getUserPermissions(user.getUserId()));
        String dataScope = permissionService.getUserDataScope(user.getUserId());
        return WxLoginVO.builder()
                .status(1)
                .needBind(false)
                .userId(user.getUserId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .roleId(user.getRoleId())
                .token(accessToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAppAccessTtl() / 1000)
                .refreshExpiresIn(jwtUtil.getAppRefreshTtl() / 1000)
                .mustChangePassword(user.getMustChangePassword())
                .passwordExpired(isPasswordExpired(user))
                .userInfo(WxLoginVO.UserInfo.builder()
                        .userId(user.getUserId()).username(user.getUsername())
                        .nickname(user.getNickname()).roleId(user.getRoleId())
                        .permissions(permissions).dataScope(dataScope).build())
                .availableRoles(List.of())
                .build();
    }

    private boolean isWechatPlaceholder(SysUser user) {
        return user.getRoleId() == null && "待绑定微信".equals(user.getNickname())
                && user.getUsername() != null && user.getUsername().matches("wx_[0-9a-f]{32}");
    }

    private void requireWechatAccountRole(SysUser user) {
        SysRole role = user.getRoleId() == null ? null : sysRoleMapper.selectById(user.getRoleId());
        if (role == null || !"0".equals(role.getStatus())) {
            throw new BaseException("该系统账号尚未配置有效身份，请联系管理员");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WxBindVO bindUser(Long userId, WxBindDTO dto) {
        if (!"existing".equals(dto.getBindType())) {
            throw new BaseException("请绑定已有系统账号，账号身份由管理员分配");
        }
        SysUser pending = sysUserMapper.selectById(userId);
        if (pending == null || !isWechatPlaceholder(pending) || pending.getRoleId() != null
                || pending.getOpenid() == null || pending.getOpenid().isBlank()) {
            throw new BaseException("微信绑定已失效，请重新微信登录");
        }
        if (dto.getUsername() == null || dto.getUsername().isBlank()
                || dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BaseException("请输入用户名和密码");
        }
        SysUser target = sysUserMapper.getByUsername(dto.getUsername().trim());
        if (target == null || isWechatPlaceholder(target)) {
            throw new BaseException("系统账号不存在");
        }
        if ("1".equals(target.getStatus())) {
            throw new BaseException(MessageConstant.ACCOUNT_DISABLED);
        }
        if (loginFailService.isLocked(target.getUsername())) {
            throw new BaseException("账号已被锁定，请" + LOCK_MINUTES + "分钟后再试");
        }
        if (!ENCODER.matches(dto.getPassword(), target.getPassword())) {
            loginFailService.incrementFailCount(target.getUsername());
            throw new BaseException(MessageConstant.PASSWORD_ERROR);
        }
        requireWechatAccountRole(target);

        // 先释放占位记录的唯一 openid；后续任一操作失败都会回滚。
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser> release =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        release.eq(SysUser::getUserId, userId).eq(SysUser::getOpenid, pending.getOpenid())
                .isNull(SysUser::getRoleId).set(SysUser::getOpenid, null);
        if (sysUserMapper.update(null, release) != 1) {
            throw new BaseException("微信绑定状态已变化，请重新微信登录");
        }
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser> bind =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        bind.eq(SysUser::getUserId, target.getUserId());
        if (target.getOpenid() == null) bind.isNull(SysUser::getOpenid);
        else bind.eq(SysUser::getOpenid, target.getOpenid());
        bind.set(SysUser::getOpenid, pending.getOpenid())
                .set(SysUser::getLoginFailCount, 0)
                .set(SysUser::getLockedUntil, null)
                .set(SysUser::getLastLoginTime, LocalDateTime.now());
        if (sysUserMapper.update(null, bind) != 1) {
            throw new BaseException("系统账号绑定状态已变化，请重试");
        }
        refreshTokenService.remove(new LambdaQueryWrapper<com.bikeassembly.entity.UserRefreshToken>()
                .eq(com.bikeassembly.entity.UserRefreshToken::getUserId, userId));
        passwordHistoryMapper.delete(new LambdaQueryWrapper<SysUserPasswordHistory>()
                .eq(SysUserPasswordHistory::getUserId, userId));
        if (sysUserMapper.deleteById(userId) != 1) {
            throw new BaseException("微信绑定状态已变化，请重试");
        }
        loginFailService.resetFail(target.getUsername());
        permissionService.evictUserCache(userId);

        String accessToken = jwtUtil.createAppAccessToken(target.getUserId(), target.getUsername());
        String refreshToken = jwtUtil.createAppRefreshToken(target.getUserId(), target.getUsername());
        refreshTokenService.saveRefreshToken(target.getUserId(), JwtUtil.CLIENT_TYPE_APP,
                jwtUtil.getTokenJti(refreshToken), refreshToken, jwtUtil.getAppRefreshTtl(), null, null);
        return WxBindVO.builder()
                .userId(target.getUserId()).username(target.getUsername())
                .nickname(target.getNickname()).roleId(target.getRoleId())
                .token(accessToken).accessToken(accessToken).refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getAppAccessTtl() / 1000)
                .refreshExpiresIn(jwtUtil.getAppRefreshTtl() / 1000)
                .mustChangePassword(target.getMustChangePassword())
                .passwordExpired(isPasswordExpired(target))
                .permissions(new ArrayList<>(permissionService.getUserPermissions(target.getUserId())))
                .dataScope(permissionService.getUserDataScope(target.getUserId()))
                .build();
    }

    // ========== CRUD 方法 ==========

    @Override
    public SysUserVO saveUser(SysUserDTO dto) {
        checkUsernameUnique(dto.getUsername(), null);

        SysUser entity = toEntity(dto);
        String rawPassword = entity.getPassword();
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BaseException("密码不能为空");
        }
        // 密码复杂度校验
        PasswordValidator.Result vr = PasswordValidator.validate(rawPassword, entity.getUsername(), 8, 64);
        if (!vr.valid) throw new BaseException(vr.message);

        entity.setPassword(ENCODER.encode(rawPassword));
        entity.setPasswordChangedAt(LocalDateTime.now());
        entity.setMustChangePassword(1);
        sysUserMapper.insert(entity);
        log.info("新增用户成功：userId={}, username={}", entity.getUserId(), entity.getUsername());

        return toVO(entity);
    }

    @Override
    public SysUserVO updateUser(Long id, SysUserDTO dto) {
        SysUser existing = sysUserMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        checkUsernameUnique(dto.getUsername(), id);

        // 记录修改前的状态，用于判断是否需要撤销 Token
        String oldStatus = existing.getStatus();
        Long oldRoleId = existing.getRoleId();

        SysUser entity = toEntity(dto);
        entity.setUserId(id);
        // 密码处理：前端可能不传密码（只改昵称/角色），此时保留原密码
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            entity.setPassword(ENCODER.encode(dto.getPassword()));
        } else {
            entity.setPassword(existing.getPassword());
        }
        sysUserMapper.updateById(entity);
        log.info("修改用户成功：userId={}", id);

        // 角色变更时清除用户权限缓存
        if (dto.getRoleId() != null && !dto.getRoleId().equals(oldRoleId)) {
            permissionService.evictUserCache(id);
            // 角色发生关键变更：撤销所有 Refresh Token
            refreshTokenService.revokeAllUserTokens(id);
            log.info("用户角色变更，已撤销所有 Refresh Token：userId={}", id);
        }

        // 状态从启用变为禁用时：撤销所有 Refresh Token
        if ("0".equals(oldStatus) && "1".equals(dto.getStatus())) {
            refreshTokenService.revokeAllUserTokens(id);
            log.info("用户被禁用，已撤销所有 Refresh Token：userId={}", id);
        }

        SysUser updated = sysUserMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public SysUserVO getUserById(Long id) {
        SysUser entity = sysUserMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public PageResult<SysUserVO> listUsers(Long page, Long size, String username, String nickname) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(username != null && !username.isBlank(), SysUser::getUsername, username)
               .like(nickname != null && !nickname.isBlank(), SysUser::getNickname, nickname);
        Page<SysUser> pageParam = new Page<>(page, size);
        IPage<SysUser> pageResult = sysUserMapper.selectPage(pageParam, wrapper);
        List<SysUserVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteUser(Long id) {
        SysUser existing = sysUserMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        sysUserMapper.deleteById(id);
        log.info("删除用户成功：userId={}", id);
    }

    // ==================== 密码安全 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BaseException(MessageConstant.DATA_NOT_FOUND);

        // 验证旧密码：首次强制改密或密码过期时可跳过旧密码校验
        boolean isForcedChange = (user.getMustChangePassword() != null && user.getMustChangePassword() == 1)
                || (user.getPasswordExpireAt() != null && user.getPasswordExpireAt().isBefore(LocalDateTime.now()));

        if (!isForcedChange) {
            // 普通主动修改密码：必须校验旧密码
            if (oldPassword == null || !ENCODER.matches(oldPassword, user.getPassword())) {
                throw new BaseException("旧密码错误");
            }
        }

        // 新密码复杂度校验
        PasswordValidator.Result vr = PasswordValidator.validate(newPassword, user.getUsername(), 8, 64);
        if (!vr.valid) throw new BaseException(vr.message);

        // 新旧密码不能相同
        if (ENCODER.matches(newPassword, user.getPassword())) {
            throw new BaseException("新密码不能与当前密码相同");
        }

        // 历史密码检查
        java.util.List<String> historyHashes = getRecentPasswordHashes(userId);
        for (String hash : historyHashes) {
            if (ENCODER.matches(newPassword, hash)) {
                throw new BaseException("该密码近期已经使用过");
            }
        }

        // 保存旧密码到历史
        savePasswordToHistory(userId, user.getPassword());

        // 更新密码
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setPassword(ENCODER.encode(newPassword));
        update.setPasswordChangedAt(LocalDateTime.now());
        update.setMustChangePassword(0);
        // 重新计算密码过期时间
        update.setPasswordExpireAt(calculatePasswordExpireAt());
        sysUserMapper.updateById(update);

        // 撤销所有 Refresh Token
        refreshTokenService.revokeAllUserTokens(userId);

        log.info("密码修改成功：userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long userId, String newPassword) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BaseException(MessageConstant.DATA_NOT_FOUND);

        // 新密码复杂度校验
        PasswordValidator.Result vr = PasswordValidator.validate(newPassword, user.getUsername(), 8, 64);
        if (!vr.valid) throw new BaseException(vr.message);

        // 更新密码 + 强制下次登录修改 + 重新计算过期时间
        SysUser update = new SysUser();
        update.setUserId(userId);
        update.setPassword(ENCODER.encode(newPassword));
        update.setPasswordChangedAt(LocalDateTime.now());
        update.setMustChangePassword(1); // 管理员重置后必须修改
        update.setPasswordExpireAt(null); // 重置后立即过期，强制修改
        sysUserMapper.updateById(update);

        // 撤销所有 Refresh Token
        refreshTokenService.revokeAllUserTokens(userId);

        log.info("管理员重置密码：userId={}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockAccount(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) throw new BaseException(MessageConstant.DATA_NOT_FOUND);

        // 使用 LambdaUpdateWrapper 强制更新 null 值（MyBatis-Plus updateById 默认忽略 null）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUser> wrapper =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.eq(SysUser::getUserId, userId)
               .set(SysUser::getLoginFailCount, 0)
               .set(SysUser::getLockedUntil, null);
        sysUserMapper.update(null, wrapper);
        loginFailService.resetFail(user.getUsername());

        log.info("管理员解锁账号：userId={}", userId);
    }

    private void savePasswordToHistory(Long userId, String passwordHash) {
        SysUserPasswordHistory history = new SysUserPasswordHistory();
        history.setUserId(userId);
        history.setPasswordHash(passwordHash);
        history.setCreateTime(LocalDateTime.now());
        passwordHistoryMapper.insert(history);

        // 只保留最近 N 次历史
        java.util.List<SysUserPasswordHistory> all = passwordHistoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserPasswordHistory>()
                        .eq(SysUserPasswordHistory::getUserId, userId)
                        .orderByDesc(SysUserPasswordHistory::getCreateTime));
        if (all.size() > PASSWORD_HISTORY_COUNT) {
            for (int i = PASSWORD_HISTORY_COUNT; i < all.size(); i++) {
                passwordHistoryMapper.deleteById(all.get(i).getHistoryId());
            }
        }
    }

    private java.util.List<String> getRecentPasswordHashes(Long userId) {
        java.util.List<SysUserPasswordHistory> list = passwordHistoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserPasswordHistory>()
                        .eq(SysUserPasswordHistory::getUserId, userId)
                        .orderByDesc(SysUserPasswordHistory::getCreateTime)
                        .last("LIMIT " + PASSWORD_HISTORY_COUNT));
        return list.stream().map(SysUserPasswordHistory::getPasswordHash)
                .collect(java.util.stream.Collectors.toList());
    }

    // ========== Refresh Token ==========

    @Override
    public RefreshTokenService.RefreshResult refreshToken(String rawRefreshToken, String clientType) {
        return refreshTokenService.rotateRefreshToken(rawRefreshToken, clientType);
    }

    @Override
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            // 解析 Refresh Token 的 JTI，用于精确撤销数据库记录
            String tokenJti = jwtUtil.getTokenJti(rawRefreshToken);
            if (tokenJti != null) {
                refreshTokenService.revokeRefreshToken(tokenJti);
                log.info("用户安全退出：tokenJti={}", tokenJti);
            }
        }
    }

    // ========== 私有方法 ==========

    /**
     * 根据系统配置计算密码过期时间。
     * 返回 null 表示永不过期（expire_days=0 或未配置）。
     */
    private LocalDateTime calculatePasswordExpireAt() {
        int days = 90; // 默认 90 天
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.bikeassembly.entity.SysConfig> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(com.bikeassembly.entity.SysConfig::getConfigKey, "password.expire_days");
            com.bikeassembly.entity.SysConfig config = sysConfigMapper.selectOne(wrapper);
            if (config != null) {
                days = Integer.parseInt(config.getConfigValue());
            }
        } catch (Exception ignored) {
            // 配置读取失败时使用默认值
        }
        if (days <= 0) return null; // 0 或负数表示永不过期
        return LocalDateTime.now().plusDays(days);
    }

    /**
     * 判断用户密码是否已过期。
     * passwordExpireAt 为空或密码有效期配置为0时，返回 false。
     */
    private boolean isPasswordExpired(SysUser user) {
        if (user.getPasswordExpireAt() == null) {
            return false;
        }
        // 读取密码过期天数配置
        try {
            com.bikeassembly.vo.SysConfigVO config = sysConfigService.getByKey("password.expire_days");
            if (config != null && "0".equals(config.getConfigValue())) {
                return false; // 密码永不过期
            }
        } catch (Exception ignored) {
            // 读取配置失败时按有到期时间判断
        }
        return user.getPasswordExpireAt().isBefore(LocalDateTime.now());
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(SysUser::getUserId, excludeId);
        }
        if (sysUserMapper.selectCount(wrapper) > 0) {
            throw new BaseException("用户名已存在：" + username);
        }
    }

    private SysUser toEntity(SysUserDTO dto) {
        SysUser entity = new SysUser();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private SysUserVO toVO(SysUser entity) {
        SysUserVO vo = new SysUserVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
