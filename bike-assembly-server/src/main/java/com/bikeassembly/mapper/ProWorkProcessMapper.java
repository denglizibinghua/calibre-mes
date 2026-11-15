package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.entity.ProWorkProcess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface ProWorkProcessMapper extends BaseMapper<ProWorkProcess> {

    IPage<ProWorkProcess> selectAppPageByCondition(Page<ProWorkProcess> page,
            @Param("assigneeUserId") Long assigneeUserId, @Param("workOrderId") Long workOrderId);

    /**
     * 分页查询工序记录（含操作员姓名，LEFT JOIN sys_user）。
     * assigneeUserId 不为 null 时过滤工单指派执行人。
     * 始终 JOIN sys_user 获取 operator 显示名。
     */
    IPage<ProWorkProcess> selectPageByCondition(Page<ProWorkProcess> page,
                                                 @Param("operatorUserId") Long operatorUserId,
                                                 @Param("assigneeUserId") Long assigneeUserId);

    /**
     * 按 ID 查询工序记录（含操作员姓名，LEFT JOIN sys_user）。
     */
    ProWorkProcess selectByIdWithOperator(@Param("processRecordId") Long processRecordId);

    /**
     * 原子累加报工数量，带计划数量上限校验。
     * 受影响行数为 0 时表示：版本冲突 / 超量报工 / 记录不存在。
     */
    int accumulateReportQty(@Param("processRecordId") Long processRecordId,
                            @Param("version") Integer version,
                            @Param("reportQty") BigDecimal reportQty);

    /**
     * 获取工单下最大工序顺序号。
     */
    int selectMaxStepSeqByWorkOrderId(@Param("workOrderId") Long workOrderId);
}
