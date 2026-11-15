package com.bikeassembly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bikeassembly.entity.ProIdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface ProIdempotencyRecordMapper extends BaseMapper<ProIdempotencyRecord> {

    /**
     * 原子占位：INSERT ... ON CONFLICT DO NOTHING 不做任何更新（幂等键冲突时返回 0）。
     * 受影响行数 = 1 表示成功占位，= 0 表示 key 已存在。
     */
    int insertIfAbsent(@Param("record") ProIdempotencyRecord record);

    ProIdempotencyRecord selectByIdForUpdate(@Param("id") long id);

    int reacquire(@Param("id") long id, @Param("expectedStatus") String expectedStatus,
                  @Param("cutoff") LocalDateTime cutoff);

    /**
     * 更新处理结果（成功或失败）。
     */
    int updateResult(@Param("id") Long id,
                     @Param("processStatus") String processStatus,
                     @Param("responseCode") Integer responseCode,
                     @Param("responseData") String responseData,
                     @Param("businessId") Long businessId);

    /**
     * 仅更新处理状态和时间戳（用于超时恢复、失败重试等场景）。
     * 不修改 businessId、responseCode、responseData。
     */
    int updateStatusOnly(@Param("id") Long id,
                         @Param("processStatus") String processStatus);
}
