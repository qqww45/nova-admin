package com.example.admin.application.service.impl;

import com.example.admin.application.service.DictAppService;
import com.example.admin.domain.model.dict.DictItem;
import com.example.admin.domain.repository.DictRepository;
import com.example.admin.interfaces.dto.resp.DictResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 业务字典应用服务实现。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Service
@RequiredArgsConstructor
public class DictAppServiceImpl implements DictAppService {

    private final DictRepository dictRepository;

    @Override
    public List<DictResp> listByCode(String dictCode) {
        return dictRepository.listByCode(dictCode).stream().map(this::toResp).toList();
    }

    @Override
    public List<DictResp> listAll() {
        return dictRepository.listAll().stream().map(this::toResp).toList();
    }

    private DictResp toResp(DictItem item) {
        DictResp resp = new DictResp();
        resp.setDictCode(item.getDictCode());
        resp.setDictValue(item.getDictValue());
        resp.setDictLabel(item.getDictLabel());
        resp.setSort(item.getSort());
        return resp;
    }
}
