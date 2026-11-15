package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bikeassembly.entity.WmsStockDoc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WmsStockDocMapper extends BaseMapper<WmsStockDoc> {

    WmsStockDoc selectByIdForUpdate(@Param("docId") Long docId);

    IPage<WmsStockDoc> selectPageByCondition(Page<WmsStockDoc> page,
                                              @Param("docNo") String docNo,
                                              @Param("docType") String docType,
                                              @Param("status") String status,
                                              @Param("keyword") String keyword);
}
