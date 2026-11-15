package com.bikeassembly.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 微信小程序登录请求 DTO。
 */
@Data
public class WxLoginDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 微信 wx.login 返回的临时 code */
    private String code;

    /** 微信昵称（可选，wx.getUserProfile 获取） */
    private String nickname;

    /** 微信头像 URL（可选） */
    private String avatar;
}
