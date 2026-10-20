package com.example.admin.domain.port;

import com.example.admin.domain.model.auth.LoginUser;

/**
 * 登录用户（含角色与权限）缓存端口。
 *
 * <p>为什么需要它：认证过滤器每个请求都要重新走一遍"用户 → 角色 → 菜单权限"的加载，
 * 一个请求就是好几次查询，而这些数据几乎不变。代价是接口越用越慢，
 * 数据库却在做大量重复的相同查询。
 *
 * <p>为什么必须有失效接口：缓存权限是安全敏感的事。
 * 角色被收回、菜单被改、账号被停用之后，如果缓存还在，用户会继续拥有他已经没有的权限。
 * 所以这里把 {@link #evict} 与 {@link #clear} 作为端口的一部分 ——
 * 任何会改变"这个人有什么权限"的用例，都必须显式失效缓存，
 * 否则缓存留下的权限窗口就是一条越权通道。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface LoginUserCache {

    /**
     * 取缓存的登录用户（返回副本，调用方可安全修改请求态字段）。
     *
     * @param username 用户名
     * @return 登录用户；未命中或已过期返回 null
     */
    LoginUser get(String username);

    /**
     * 缓存登录用户（实现内部必须保存副本，不能持有调用方传入的对象引用）。
     *
     * @param username  用户名
     * @param loginUser 登录用户
     */
    void put(String username, LoginUser loginUser);

    /**
     * 失效单个用户（资料、状态、角色分配变化时使用）。
     *
     * @param username 用户名
     */
    void evict(String username);

    /**
     * 失效全部（角色权限、菜单权限等会影响一批用户的变更时使用）。
     */
    void clear();
}
