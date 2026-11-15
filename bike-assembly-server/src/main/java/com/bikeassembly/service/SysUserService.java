package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.SysUserDTO;
import com.bikeassembly.dto.UserLoginDTO;
import com.bikeassembly.dto.WxBindDTO;
import com.bikeassembly.dto.WxLoginDTO;
import com.bikeassembly.entity.SysUser;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.SysUserVO;
import com.bikeassembly.vo.UserLoginVO;
import com.bikeassembly.vo.WxBindVO;
import com.bikeassembly.vo.WxLoginVO;

public interface SysUserService extends IService<SysUser> {

    /**
     * 用户登录（管理端）。
     */
    UserLoginVO login(UserLoginDTO dto);

    /**
     * App 端用户名密码登录（生成 App 端 Token）。
     */
    UserLoginVO appLogin(UserLoginDTO dto);

    /**
     * 人脸服务完成匹配后的统一 Token 签发入口。
     */
    UserLoginVO faceLogin(Long userId, String clientType);

    /**
     * 微信小程序登录。
     */
    WxLoginVO wxLogin(WxLoginDTO dto);

    /**
     * 微信用户绑定/注册。
     */
    WxBindVO bindUser(Long userId, WxBindDTO dto);

    SysUserVO saveUser(SysUserDTO dto);

    SysUserVO updateUser(Long id, SysUserDTO dto);

    SysUserVO getUserById(Long id);

    PageResult<SysUserVO> listUsers(Long page, Long size, String username, String nickname);

    void deleteUser(Long id);

    /** 修改密码（需验证旧密码） */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 管理员重置用户密码 */
    void resetPassword(Long userId, String newPassword);

    /** 管理员解锁账号 */
    void unlockAccount(Long userId);

    /** 刷新 Access Token */
    com.bikeassembly.service.RefreshTokenService.RefreshResult refreshToken(String rawRefreshToken, String clientType);

    /** 安全退出 */
    void logout(String tokenJti);
}
