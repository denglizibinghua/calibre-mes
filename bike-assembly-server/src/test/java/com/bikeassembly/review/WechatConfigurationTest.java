package com.bikeassembly.review;

import com.bikeassembly.exception.BaseException;
import com.bikeassembly.properties.WechatMiniappProperties;
import com.bikeassembly.service.impl.WechatServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatConfigurationTest {
    @Test
    void missingAppIdIsReportedBeforeCallingWechat() {
        WechatMiniappProperties properties = new WechatMiniappProperties();
        properties.setSecret("test-only");
        assertMissingConfiguration(properties);
    }

    @Test
    void blankSecretIsReportedBeforeCallingWechat() {
        WechatMiniappProperties properties = new WechatMiniappProperties();
        properties.setAppid("wx-test-only");
        properties.setSecret("   ");
        assertMissingConfiguration(properties);
    }

    private void assertMissingConfiguration(WechatMiniappProperties properties) {
        BaseException error = assertThrows(BaseException.class,
                () -> new WechatServiceImpl(properties).code2Session("test-only"));
        assertTrue(error.getMessage().contains("AppID 和 AppSecret"));
    }
}
