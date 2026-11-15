package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.entity.ProWorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProWorkOrderMapper extends BaseMapper<ProWorkOrder> {

    ProWorkOrder selectByWorkOrderNo(@Param("workOrderNo") String workOrderNo);

    int countReferences(@Param("workOrderId") Long workOrderId);

    List<ProWorkOrder> selectListByCondition(@Param("workOrderNo") String workOrderNo,
                                              @Param("orderNo") String orderNo,
                                              @Param("productName") String productName);

    IPage<ProWorkOrder> selectPageByCondition(Page<ProWorkOrder> page,
                                               @Param("workOrderNo") String workOrderNo,
                                               @Param("orderNo") String orderNo,
                                               @Param("productName") String productName,
                                               @Param("assigneeUserId") Long assigneeUserId);

    /**
     * 原子完工：同时更新 status + actualEndTime + version。
     * WHERE 条件校验原状态=IN_PROGRESS 和 version，防止并发覆盖。
     * @return 受影响行数（必须为 1）
     */
    @Update("UPDATE pro_work_order SET status = #{completedStatus}, "
            + "actual_end_time = #{actualEndTime}, version = version + 1 "
            + "WHERE work_order_id = #{workOrderId} "
            + "AND status = #{inProgressStatus} "
            + "AND version = #{version}")
    int completeWorkOrderAtomically(@Param("workOrderId") Long workOrderId,
                                    @Param("completedStatus") String completedStatus,
                                    @Param("inProgressStatus") String inProgressStatus,
                                    @Param("actualEndTime") java.time.LocalDateTime actualEndTime,
                                    @Param("version") Integer version);
}
