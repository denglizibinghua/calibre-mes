package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.QmsInspectionDTO;
import com.bikeassembly.entity.QmsInspection;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.QmsInspectionVO;

public interface QmsInspectionService extends IService<QmsInspection> {

    QmsInspectionVO getQmsInspectionById(Long id);

    PageResult<QmsInspectionVO> listQmsInspections(Long page, Long size, String inspectionNo, String itemName, String inspector, String scope, Long processRecordId);

    PageResult<QmsInspectionVO> listAppQmsInspections(String scope, Long workOrderId, Long processId, String status);

    long countAccessiblePending(Long workOrderId);

    /** 录入检验结果：待检(0) → 已录入(1) */
    QmsInspectionVO enterInspectionResult(Long id, QmsInspectionDTO dto);

    /** 校验质检单读取归属 */
    void validateInspectionOwnershipForRead(Long inspectionId);

    /** 确认质检：已录入(1) → 已确认(2) */
    QmsInspectionVO confirmInspection(Long id);
}
