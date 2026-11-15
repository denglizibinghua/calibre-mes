package com.bikeassembly.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.bikeassembly.dto.QmsInspectionItemDTO;
import com.bikeassembly.entity.QmsInspectionItem;
import com.bikeassembly.result.PageResult;
import com.bikeassembly.vo.QmsInspectionItemVO;

public interface QmsInspectionItemService extends IService<QmsInspectionItem> {

    QmsInspectionItemVO saveQmsInspectionItem(QmsInspectionItemDTO dto);

    QmsInspectionItemVO updateQmsInspectionItem(Long id, QmsInspectionItemDTO dto);

    QmsInspectionItemVO getQmsInspectionItemById(Long id);

    PageResult<QmsInspectionItemVO> listQmsInspectionItems(Long page, Long size);

    void deleteQmsInspectionItem(Long id);
}
