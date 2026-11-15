package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.entity.QmsInspection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QmsInspectionMapper extends BaseMapper<QmsInspection> {

    IPage<QmsInspection> selectAppPageByCondition(Page<QmsInspection> page,
            @Param("userId") Long userId, @Param("viewAll") boolean viewAll,
            @Param("includePending") boolean includePending, @Param("pendingOnly") boolean pendingOnly,
            @Param("workOrderId") Long workOrderId, @Param("processId") Long processId,
            @Param("status") String status);

    long countAppPending(@Param("workOrderId") Long workOrderId, @Param("userId") Long userId,
            @Param("viewAll") boolean viewAll, @Param("includePending") boolean includePending);

    int countReferences(@Param("inspectionId") Long inspectionId);

    List<QmsInspection> selectListByCondition(@Param("inspectionNo") String inspectionNo,
                                               @Param("itemName") String itemName,
                                               @Param("inspector") String inspector);

    IPage<QmsInspection> selectPageByCondition(Page<QmsInspection> page,
                                                @Param("inspectionNo") String inspectionNo,
                                                @Param("itemName") String itemName,
                                                @Param("inspector") String inspector,
                                                @Param("inspectorUserId") Long inspectorUserId,
                                                @Param("isPending") Boolean isPending,
                                                @Param("includePending") Boolean includePending,
                                                @Param("processRecordId") Long processRecordId);
}
