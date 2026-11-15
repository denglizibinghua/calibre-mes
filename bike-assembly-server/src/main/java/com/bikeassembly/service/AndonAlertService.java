package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.AndonAlertDTO;
import com.bikeassembly.dto.AndonAlertEscalateDTO;
import com.bikeassembly.entity.AndonAlert;
import com.bikeassembly.vo.AndonAlertVO;
import com.bikeassembly.result.PageResult;


public interface AndonAlertService extends IService<AndonAlert> {

    AndonAlertVO saveAndonAlert(AndonAlertDTO dto);

    AndonAlertVO updateAndonAlert(Long id, AndonAlertDTO dto);

    AndonAlertVO getAndonAlertById(Long id);

    PageResult<AndonAlertVO> listAndonAlerts(Long page, Long size, String scope, String relation);

    void deleteAndonAlert(Long id);

    /** 受理告警：将待处理状态变为处理中 */
    AndonAlertVO acceptAndonAlert(Long id);

    /** 关闭告警：将处理中状态变为已关闭 */
    AndonAlertVO closeAndonAlert(Long id);

    /** 升级告警：待处理(0)/处理中(1) 可升级，记录升级人/时间/备注 */
    AndonAlertVO escalateAndonAlert(Long id, AndonAlertEscalateDTO dto);
}
