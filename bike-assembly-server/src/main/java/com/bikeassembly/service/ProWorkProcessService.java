package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.ProWorkProcessDTO;
import com.bikeassembly.entity.ProWorkProcess;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.ProWorkProcessVO;

public interface ProWorkProcessService extends IService<ProWorkProcess> {

    ProWorkProcessVO saveProWorkProcess(ProWorkProcessDTO dto);

    ProWorkProcessVO updateProWorkProcess(Long id, ProWorkProcessDTO dto);

    ProWorkProcessVO getProWorkProcessById(Long id);

    PageResult<ProWorkProcessVO> listProWorkProcesses(Long page, Long size, String scope);

    PageResult<ProWorkProcessVO> listAppProWorkProcesses(String scope, Long workOrderId);

    void deleteProWorkProcess(Long id);

    /** 开始工序：READY → IN_PROGRESS */
    ProWorkProcessVO startProWorkProcess(Long id);

    /** 报工并送检：IN_PROGRESS → WAIT_INSPECTION */
    ProWorkProcessVO reportWithInspection(Long id, java.math.BigDecimal reportQty, String qcResultNote);

    /**
     * [内部方法] 质检通过：WAIT_INSPECTION → QUALIFIED → COMPLETED。
     * 仅供 QmsInspectionServiceImpl.confirmInspection() 内部调用，禁止 Controller 直接调用。
     * 所有质检判定必须通过质检单确认流程。
     */
    @Deprecated
    ProWorkProcessVO qcPass(Long id, String qcNote);

    /**
     * [内部方法] 质检不合格：WAIT_INSPECTION → REJECTED。
     * 仅供 QmsInspectionServiceImpl.confirmInspection() 内部调用，禁止 Controller 直接调用。
     * 所有质检判定必须通过质检单确认流程。
     */
    @Deprecated
    ProWorkProcessVO qcFail(Long id, String qcNote);

    /** [操作员] 开始返工：REJECTED → REWORK。要求当前用户是工单执行人。 */
    ProWorkProcessVO operatorReworkStart(Long id);

    /** [操作员] 返工完成：REWORK → WAIT_INSPECTION。要求当前用户是工单执行人。 */
    ProWorkProcessVO operatorReworkComplete(Long id);

    /** [管理端] 强制发起返工：REJECTED → REWORK。需 admin:work-process:rework 权限，不校验执行人归属。 */
    ProWorkProcessVO adminReworkStart(Long id, String reason);

    /** [管理端] 强制返工完成：REWORK → WAIT_INSPECTION。需 admin:work-process:rework 权限。 */
    ProWorkProcessVO adminReworkComplete(Long id, String remark);

    /** @deprecated 请使用 operatorReworkStart */
    @Deprecated
    ProWorkProcessVO reworkStart(Long id);

    /** @deprecated 请使用 operatorReworkComplete */
    @Deprecated
    ProWorkProcessVO reworkComplete(Long id);

    /** 统一报工：后端自动判断是否需要质检，决定进入 COMPLETED 或 WAIT_INSPECTION */
    ProWorkProcessVO unifiedReport(Long id, java.math.BigDecimal reportQty, String qcResultNote);

    /** 校验工序读取归属（非 view-all 用户只能读取自己工单的工序） */
    void validateProcessOwnershipForRead(Long processRecordId);

    /** 跳过工序：PENDING 或 READY → SKIPPED */
    ProWorkProcessVO skipProcess(Long id, String remark);

    /** 取消工序：PENDING → CANCELLED */
    ProWorkProcessVO cancelProcess(Long id, String remark);
}
