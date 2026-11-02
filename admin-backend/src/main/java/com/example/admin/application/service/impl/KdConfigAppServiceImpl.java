package com.example.admin.application.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.admin.application.service.KdConfigAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.auth.LoginUser;
import com.example.admin.domain.model.kd.KdBinding;
import com.example.admin.domain.model.kd.KdFieldMapping;
import com.example.admin.domain.model.kd.KdSyncTask;
import com.example.admin.domain.port.OperatorContext;
import com.example.admin.domain.repository.KdBindingRepository;
import com.example.admin.domain.repository.KdFieldMappingRepository;
import com.example.admin.domain.repository.KdSyncTaskRepository;
import com.example.admin.interfaces.dto.req.KdFieldMappingSaveReq;
import com.example.admin.interfaces.dto.req.KdTaskSaveReq;
import com.example.admin.interfaces.dto.resp.KdFieldMappingResp;
import com.example.admin.interfaces.dto.resp.KdSyncTaskResp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 同步配置应用服务实现。
 *
 * @author admin-system
 * @since 2026-11-02
 */
@Service
@RequiredArgsConstructor
public class KdConfigAppServiceImpl implements KdConfigAppService {

    private final KdSyncTaskRepository taskRepository;
    private final KdFieldMappingRepository mappingRepository;
    private final KdBindingRepository bindingRepository;
    private final OperatorContext operatorContext;

    @Override
    public List<KdSyncTaskResp> listTasks() {
        return taskRepository.listAll().stream().map(this::toTaskResp).toList();
    }

    @Override
    public String saveTask(KdTaskSaveReq req) {
        String acct = StrUtil.blankToDefault(StrUtil.trim(req.getAcctId()), KdSyncTask.GLOBAL);
        String entity = StrUtil.trim(req.getEntity());

        KdSyncTask sameKey = taskRepository.findByAcctAndEntity(acct, entity);
        KdSyncTask task;
        Long id = IdParser.parseIdOrNull(req.getId());
        if (id != null) {
            task = taskRepository.findById(id);
            if (task == null) {
                throw new BusinessException(ResultCode.DATA_NOT_FOUND, "任务不存在");
            }
            if (sameKey != null && !sameKey.getId().equals(id)) {
                // 数据库有唯一索引，但直接抛 SQL 异常前端只能看到 500；
                // 这里提前拦成业务提示，运维才知道"该账套该实体已有一条任务"
                throw new BusinessException(ResultCode.PARAM_ERROR, "该账套下同一实体已存在任务，请直接编辑那一条");
            }
        } else {
            // 同一账套同一实体只允许一条：已存在则视为更新，避免页面重复提交产生两条
            task = sameKey == null ? new KdSyncTask() : sameKey;
        }
        task.setAcctId(acct);
        task.setEntity(entity);
        task.setFormId(StrUtil.trim(req.getFormId()));
        task.setEndpoint(StrUtil.trim(req.getEndpoint()));
        task.setFieldKeys(StrUtil.trim(req.getFieldKeys()));
        task.setWatermarkField(StrUtil.trim(req.getWatermarkField()));
        task.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        task.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        task.setRemark(req.getRemark());

        if (task.getId() == null) {
            return String.valueOf(taskRepository.save(task));
        }
        taskRepository.update(task);
        return String.valueOf(task.getId());
    }

    @Override
    public List<KdFieldMappingResp> listMappings(String bindingId) {
        KdBinding binding = requireOwnedBinding(IdParser.parseId(bindingId));
        return mappingRepository.listByBinding(binding.getId()).stream().map(this::toMappingResp).toList();
    }

    @Override
    public void saveMapping(KdFieldMappingSaveReq req) {
        Long bindingId = IdParser.parseId(req.getBindingId());
        requireOwnedBinding(bindingId);
        String key = StrUtil.trim(req.getMappingKey());
        KdFieldMapping mapping = mappingRepository.find(bindingId, key);
        if (mapping == null) {
            mapping = new KdFieldMapping();
            mapping.setBindingId(bindingId);
            mapping.setMappingKey(key);
            mapping.setMappingValue(req.getMappingValue());
            mapping.setRemark(req.getRemark());
            mappingRepository.save(mapping);
            return;
        }
        mapping.setMappingValue(req.getMappingValue());
        mapping.setRemark(req.getRemark());
        mappingRepository.update(mapping);
    }

    /**
     * 校验绑定存在且当前用户有权访问。
     *
     * @param bindingId 绑定 ID
     * @return 绑定
     */
    private KdBinding requireOwnedBinding(Long bindingId) {
        KdBinding binding = bindingRepository.findById(bindingId);
        if (binding == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "绑定不存在");
        }
        LoginUser loginUser = operatorContext.current();
        boolean admin = loginUser != null && loginUser.getRoles() != null
                && loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE_KEY);
        if (!admin && !binding.getUserId().equals(operatorContext.currentUserId())) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "绑定不存在");
        }
        return binding;
    }

    /**
     * 任务 → 响应。
     *
     * @param task 任务
     * @return 响应
     */
    private KdSyncTaskResp toTaskResp(KdSyncTask task) {
        KdSyncTaskResp resp = new KdSyncTaskResp();
        resp.setId(String.valueOf(task.getId()));
        resp.setAcctId(task.getAcctId());
        resp.setEntity(task.getEntity());
        resp.setFormId(task.getFormId());
        resp.setEndpoint(task.getEndpoint());
        resp.setFieldKeys(task.getFieldKeys());
        resp.setWatermarkField(task.getWatermarkField());
        resp.setEnabled(task.getEnabled());
        resp.setSortNo(task.getSortNo());
        resp.setRemark(task.getRemark());
        resp.setUpdateTime(task.getUpdateTime());
        return resp;
    }

    /**
     * 映射 → 响应。
     *
     * @param mapping 映射
     * @return 响应
     */
    private KdFieldMappingResp toMappingResp(KdFieldMapping mapping) {
        KdFieldMappingResp resp = new KdFieldMappingResp();
        resp.setId(String.valueOf(mapping.getId()));
        resp.setBindingId(String.valueOf(mapping.getBindingId()));
        resp.setMappingKey(mapping.getMappingKey());
        resp.setMappingValue(mapping.getMappingValue());
        resp.setRemark(mapping.getRemark());
        return resp;
    }
}
