package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.SysConfigDTO;
import com.bikeassembly.entity.SysConfig;
import com.bikeassembly.vo.SysConfigVO;
import com.bikeassembly.result.PageResult;


public interface SysConfigService extends IService<SysConfig> {

    SysConfigVO saveConfig(SysConfigDTO dto);

    SysConfigVO updateConfig(Long id, SysConfigDTO dto);

    SysConfigVO getConfigById(Long id);

    /** 按配置键读取（带 Redis 缓存）；不存在返回 null */
    SysConfigVO getByKey(String configKey);

    PageResult<SysConfigVO> listConfigs(Long page, Long size, String configKey, String configName);

    void deleteConfig(Long id);
}
