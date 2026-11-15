package com.bikeassembly.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bikeassembly.constant.MessageConstant;
import com.bikeassembly.dto.SysConfigDTO;
import com.bikeassembly.entity.SysConfig;
import com.bikeassembly.exception.BaseException;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.mapper.SysConfigMapper;
import com.bikeassembly.service.SysConfigService;
import com.bikeassembly.utils.RedisService;
import com.bikeassembly.vo.SysConfigVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.stream.Collectors;


@Service
@Slf4j
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Autowired
    private SysConfigMapper sysConfigMapper;
    @Autowired
    private RedisService redisService;

    /** 配置缓存 TTL */
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final String CONFIG_ID_KEY = "config:byId:";
    private static final String CONFIG_KEY_KEY = "config:key:";

    @Override
    public SysConfigVO saveConfig(SysConfigDTO dto) {
        checkConfigKeyUnique(dto.getConfigKey(), null);

        SysConfig entity = toEntity(dto);
        sysConfigMapper.insert(entity);
        evictConfigCache();
        log.info("新增系统配置成功：configId={}, configKey={}", entity.getConfigId(), entity.getConfigKey());

        return toVO(entity);
    }

    @Override
    public SysConfigVO updateConfig(Long id, SysConfigDTO dto) {
        SysConfig existing = sysConfigMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }

        checkConfigKeyUnique(dto.getConfigKey(), id);

        SysConfig entity = toEntity(dto);
        entity.setConfigId(id);
        sysConfigMapper.updateById(entity);
        evictConfigCache();
        log.info("修改系统配置成功：configId={}", id);

        SysConfig updated = sysConfigMapper.selectById(id);
        return toVO(updated);
    }

    @Override
    public SysConfigVO getConfigById(Long id) {
        SysConfigVO cached = redisService.get(CONFIG_ID_KEY + id, SysConfigVO.class);
        if (cached != null) {
            return cached;
        }
        SysConfig entity = sysConfigMapper.selectById(id);
        if (entity == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        SysConfigVO vo = toVO(entity);
        redisService.set(CONFIG_ID_KEY + id, vo, CACHE_TTL);
        return vo;
    }

    @Override
    public SysConfigVO getByKey(String configKey) {
        SysConfigVO cached = redisService.get(CONFIG_KEY_KEY + configKey, SysConfigVO.class);
        if (cached != null) {
            return cached;
        }
        SysConfig entity = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, configKey)
                        .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        SysConfigVO vo = toVO(entity);
        redisService.set(CONFIG_KEY_KEY + configKey, vo, CACHE_TTL);
        return vo;
    }

    @Override
    public PageResult<SysConfigVO> listConfigs(Long page, Long size, String configKey, String configName) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(configKey != null && !configKey.isBlank(), SysConfig::getConfigKey, configKey)
               .like(configName != null && !configName.isBlank(), SysConfig::getConfigName, configName);
        Page<SysConfig> pageParam = new Page<>(page, size);
        IPage<SysConfig> pageResult = sysConfigMapper.selectPage(pageParam, wrapper);
        List<SysConfigVO> records = pageResult.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(records, pageResult.getTotal(), pageResult.getCurrent(), pageResult.getSize());
    }

    @Override
    public void deleteConfig(Long id) {
        SysConfig existing = sysConfigMapper.selectById(id);
        if (existing == null) {
            throw new BaseException(MessageConstant.DATA_NOT_FOUND);
        }
        sysConfigMapper.deleteById(id);
        evictConfigCache();
        log.info("删除系统配置成功：configId={}", id);
    }

    /** 配置新增/修改/删除后清除全部配置缓存 */
    private void evictConfigCache() {
        redisService.deleteByPrefix(CONFIG_ID_KEY);
        redisService.deleteByPrefix(CONFIG_KEY_KEY);
    }

    private void checkConfigKeyUnique(String configKey, Long excludeId) {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, configKey);
        if (excludeId != null) {
            wrapper.ne(SysConfig::getConfigId, excludeId);
        }
        if (sysConfigMapper.selectCount(wrapper) > 0) {
            throw new BaseException("配置键已存在：" + configKey);
        }
    }

    private SysConfig toEntity(SysConfigDTO dto) {
        SysConfig entity = new SysConfig();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    private SysConfigVO toVO(SysConfig entity) {
        SysConfigVO vo = new SysConfigVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
