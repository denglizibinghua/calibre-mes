package com.bikeassembly.service;

/**
 * 微信服务接口。
 */
public interface WechatService {

    /**
     * 用微信临时登录 code 换取 openid。
     *
     * @param code wx.login 返回的临时 code
     * @return openid
     * @throws com.bikeassembly.exception.BaseException 微信 API 返回错误时抛出
     */
    String code2Session(String code);
}
