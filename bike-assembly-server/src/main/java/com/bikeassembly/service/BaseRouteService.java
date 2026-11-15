package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.BaseRouteDTO;
import com.bikeassembly.entity.BaseRoute;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.BaseRouteVO;

public interface BaseRouteService extends IService<BaseRoute> {

    BaseRouteVO saveRoute(BaseRouteDTO dto);

    BaseRouteVO updateRoute(Long id, BaseRouteDTO dto);

    BaseRouteVO getRouteById(Long id);

    PageResult<BaseRouteVO> listRoutes(Long page, Long size);

    void deleteRoute(Long id);
}
