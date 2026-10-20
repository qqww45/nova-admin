package com.example.admin.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.admin.infrastructure.persistence.po.SysUser;

/**
 * 用户数据访问接口。
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper} 即具备基础 CRUD；
 * 复杂查询统一使用 {@code LambdaQueryWrapper}，禁止字符串拼接 SQL。
 * 接口由启动类的 {@code @MapperScan} 扫描注册，无需单独标注 {@code @Mapper}。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
