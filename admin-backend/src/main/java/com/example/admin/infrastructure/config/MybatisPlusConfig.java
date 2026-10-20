package com.example.admin.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.example.admin.common.page.PageQuery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>要点：
 * <ul>
 *     <li>分页插件：不显式指定 {@code DbType}，由 MyBatis-Plus 依据 JDBC 连接元数据自动识别，
 *         这样 SQLite / MySQL / PostgreSQL 切换时无需改动代码（跨库兼容要求）；</li>
 *     <li>单页最大条数与 {@link PageQuery#MAX_PAGE_SIZE} 保持一致（100），
 *         双保险防止恶意大分页拖垮数据库；</li>
 *     <li>未引入租户/数据权限插件，后续如需扩展在此追加 innerInterceptor 即可；</li>
 *     <li>乐观锁插件（{@link OptimisticLockerInnerInterceptor}）：为"客户-订单-出货"新增。
 *         订单头与明细存在并发出货回写，必须防止后写覆盖先写；
 *         该插件只对标记了 {@code @Version} 的实体生效，
 *         系统管理模块的现有实体（sys_user 等）均无该注解，行为完全不受影响。</li>
 * </ul>
 *
 * @author admin-system
 * @since 2026-01-01
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 插件链（分页、防全表更新等）。
     *
     * @return 插件拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        // 单页条数上限，与入参校验共同构成“双保险”
        paginationInnerInterceptor.setMaxLimit(Long.valueOf(PageQuery.MAX_PAGE_SIZE));
        // 超过最大页码时回到首页，避免返回空列表造成前端困惑
        paginationInnerInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);

        // 乐观锁：仅对带 @Version 字段的实体生效（订单头 / 订单明细），不影响既有实体
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }
}
