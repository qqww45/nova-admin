package com.example.admin.application.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.example.admin.application.assembler.DeptAssembler;
import com.example.admin.application.service.DeptAppService;
import com.example.admin.common.constant.Constants;
import com.example.admin.common.exception.BusinessException;
import com.example.admin.common.result.ResultCode;
import com.example.admin.common.util.IdParser;
import com.example.admin.domain.model.dept.Dept;
import com.example.admin.domain.repository.DeptRepository;
import com.example.admin.domain.service.TreeDomainService;
import com.example.admin.interfaces.dto.req.DeptSaveReq;
import com.example.admin.interfaces.dto.resp.DeptVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import java.util.List;

/**
 * 部门应用服务实现。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeptAppServiceImpl implements DeptAppService {

    private final DeptRepository deptRepository;

    private final DeptAssembler deptAssembler;

    /**
     * 查询部门树。
     *
     * @param deptName 部门名称（模糊，可为空）
     * @param status   状态（可为空）
     * @return 部门树
     */
    @Override
    public List<DeptVo> tree(String deptName, Integer status) {
        List<Dept> depts = deptRepository.findAll(deptName, status);
        List<Dept> tree = TreeDomainService.build(depts, Dept::getId, Dept::getParentId, Dept::setChildren,
                Comparator.comparing(dept -> dept.getSort() == null ? 0 : dept.getSort()));
        return deptAssembler.toVoList(tree);
    }

    /**
     * 新增部门。
     *
     * @param req 部门参数
     * @return 新部门 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(DeptSaveReq req) {
        checkParent(req.getParentId(), null);
        Dept dept = new Dept();
        fillDept(dept, req);
        deptRepository.save(dept);
        return String.valueOf(dept.getId());
    }

    /**
     * 修改部门（父部门不能是自己或自己的子孙）。
     *
     * @param req 部门参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DeptSaveReq req) {
        Long deptId = IdParser.parseId(req.getId());
        Dept dept = deptRepository.findById(deptId)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_NOT_FOUND));
        checkParent(req.getParentId(), deptId);
        fillDept(dept, req);
        deptRepository.save(dept);
    }

    /**
     * 批量删除部门（含子部门）。
     *
     * @param ids 部门 ID 集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(List<String> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        List<Long> deptIds = new ArrayList<>();
        for (String id : ids) {
            deptIds.add(IdParser.parseId(id));
        }
        // 递归收集子孙部门（树结构的领域规则，此处按父 ID 逐层展开）
        List<Dept> all = deptRepository.findAll(null, null);
        List<Long> allIds = collectChildren(all, deptIds);
        // 一次性统计哪些部门还有用户（循环里逐条 count 会在批量删除时放大成 N 次查询）
        Map<Long, Long> userCounts = deptRepository.countUsersByDeptIds(allIds);
        for (Long deptId : allIds) {
            // 逻辑外键：部门下存在用户时不允许删除（应用层维护，跨库兼容）
            if (userCounts.getOrDefault(deptId, 0L) > 0) {
                throw new BusinessException(ResultCode.BIZ_ERROR, "部门下存在用户，不允许删除");
            }
        }
        deptRepository.deleteByIds(allIds);
    }

    /**
     * 获取部门名称。
     *
     * @param deptId 部门 ID
     * @return 部门名称
     */
    @Override
    public String getDeptName(Long deptId) {
        return deptRepository.findNameById(deptId);
    }

    /**
     * 校验父部门合法性（存在性 + 环路检测）。
     *
     * @param parentId 父部门 ID（字符串）
     * @param selfId   当前部门 ID（新增时为 null）
     */
    private void checkParent(String parentId, Long selfId) {
        Long pid = StrUtil.isBlank(parentId) ? Constants.TOP_PARENT_ID : IdParser.parseId(parentId);
        if (selfId != null && selfId.equals(pid)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上级部门不能是自己");
        }
        if (Constants.TOP_PARENT_ID.equals(pid)) {
            return;
        }
        if (deptRepository.findById(pid).isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "上级部门不存在");
        }
        if (selfId != null) {
            List<Dept> all = deptRepository.findAll(null, null);
            if (collectChildren(all, List.of(selfId)).contains(pid)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "上级部门不能是自己的子部门");
            }
        }
    }

    /**
     * 递归收集子孙部门 ID（含自身）。
     *
     * @param all  全量部门（平铺）
     * @param ids  起始部门 ID
     * @return 全部相关部门 ID
     */
    private List<Long> collectChildren(List<Dept> all, List<Long> ids) {
        List<Long> result = new ArrayList<>(ids);
        // 同 MenuTreeDomainService.collectSubTreeIds：用 visited 防环替代"最多 10 层"的静默截断。
        // 部门层级被截断的后果更直接 —— 删除部门时漏掉深层子部门，数据库里留下孤儿记录。
        Set<Long> visited = new HashSet<>(ids);
        List<Long> current = new ArrayList<>(ids);
        while (CollUtil.isNotEmpty(current)) {
            List<Long> next = new ArrayList<>();
            for (Long id : current) {
                for (Dept dept : all) {
                    if (id.equals(dept.getParentId()) && visited.add(dept.getId())) {
                        next.add(dept.getId());
                    }
                }
            }
            result.addAll(next);
            current = next;
        }
        return result;
    }

    /**
     * 填充部门属性。
     *
     * @param dept 部门聚合
     * @param req  请求参数
     */
    private void fillDept(Dept dept, DeptSaveReq req) {
        dept.setParentId(StrUtil.isBlank(req.getParentId()) ? Constants.TOP_PARENT_ID : IdParser.parseId(req.getParentId()));
        dept.setDeptName(req.getDeptName());
        dept.setSort(req.getSort() == null ? 0 : req.getSort());
        dept.setLeader(req.getLeader());
        dept.setPhone(req.getPhone());
        dept.setEmail(req.getEmail());
        dept.setStatus(req.getStatus() == null ? Constants.STATUS_NORMAL : req.getStatus());
        dept.setRemark(req.getRemark());
    }

}
