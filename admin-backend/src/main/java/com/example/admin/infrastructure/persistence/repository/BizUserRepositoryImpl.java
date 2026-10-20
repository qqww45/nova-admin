package com.example.admin.infrastructure.persistence.repository;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.domain.model.common.IdName;
import com.example.admin.domain.repository.BizUserRepository;
import com.example.admin.infrastructure.persistence.mapper.SysUserMapper;
import com.example.admin.infrastructure.persistence.po.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 业务员只读查询实现（复用系统管理已有的 sys_user 表）。
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class BizUserRepositoryImpl implements BizUserRepository {

    /** 用户状态：0 正常（与 Constants.STATUS_NORMAL 一致） */
    private static final int STATUS_NORMAL = 0;

    private final SysUserMapper userMapper;

    @Override
    public List<IdName> findNames(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getId, ids));
        return users.stream()
                .map(u -> new IdName(u.getId(), displayName(u)))
                .toList();
    }

    @Override
    public IdName findByName(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        String keyword = name.trim();
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getNickname, keyword)
                .or().eq(SysUser::getUsername, keyword)
                .last("LIMIT 1"));
        return user == null ? null : new IdName(user.getId(), displayName(user));
    }

    @Override
    public List<IdName> listSalesmen(int limit) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, STATUS_NORMAL)
                .last("LIMIT " + Math.max(1, limit)));
        return users.stream().map(u -> new IdName(u.getId(), displayName(u))).toList();
    }

    /** 展示名：优先昵称，没有则回落登录账号 */
    private String displayName(SysUser user) {
        return StrUtil.isNotBlank(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
