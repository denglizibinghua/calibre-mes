package com.bikeassembly.service.impl;

import com.bikeassembly.exception.BaseException;
import com.bikeassembly.properties.WechatMiniappProperties;
import com.bikeassembly.service.WechatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 微信服务实现 — 调用微信官方 API。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatServiceImpl implements WechatService {

    private final WechatMiniappProperties wechatProps;

    private static final String CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session"
            + "?appid={appid}&secret={secret}&js_code={code}&grant_type=authorization_code";

    private final RestTemplate restTemplate = createRestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建配置了超时的 RestTemplate，避免微信 API 无响应时永久阻塞。
     */
    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);  // 连接超时 5 秒
        factory.setReadTimeout(10_000);    // 读取超时 10 秒
        return new RestTemplate(factory);
    }

    @Override
    public String code2Session(String code) {
        if (wechatProps.getAppid() == null || wechatProps.getAppid().isBlank()
                || wechatProps.getSecret() == null || wechatProps.getSecret().isBlank()) {
            throw new BaseException("微信登录暂不可用，请联系管理员配置小程序 AppID 和 AppSecret，或使用账号密码登录");
        }
        String url = CODE2SESSION_URL
                .replace("{appid}", wechatProps.getAppid())
                .replace("{secret}", wechatProps.getSecret())
                .replace("{code}", code);

        log.debug("请求微信 code2Session...");

        String response;
        try {
            response = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("微信 code2Session 网络请求失败", e);
            throw new BaseException("微信服务暂不可用，请稍后重试");
        }

        log.debug("微信 code2Session 返回: {}", response);

        try {
            JsonNode json = objectMapper.readTree(response);
            int errcode = json.has("errcode") ? json.get("errcode").asInt() : 0;

            if (errcode != 0) {
                String errmsg = json.has("errmsg") ? json.get("errmsg").asText() : "未知错误";
                log.error("微信 code2Session 失败: errcode={}, errmsg={}", errcode, errmsg);
                throw new BaseException("微信登录失败: " + errmsg);
            }

            String openid = json.get("openid").asText();
            log.info("微信 code2Session 成功, openid={}", openid.substring(0, 6) + "***");
            return openid;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析微信 code2Session 响应失败: {}", response, e);
            throw new BaseException("微信登录异常，请稍后重试");
        }
    }
}
