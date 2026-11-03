package com.example.admin.application.service;

import com.example.admin.interfaces.dto.req.KdMappingSaveReq;
import com.example.admin.interfaces.dto.resp.KdCustomerMappingResp;

import java.util.List;

/**
 * 金蝶客户 ↔ 内部客户映射应用服务（试点批 R4）。
 *
 * <p>内部系统与金蝶侧是两套客户体系；本服务维护「归属人 + 账套 + 金蝶内码 → 内部客户」
 * 的映射，是后续两口径联动（对照页、客户 360）的地基。建议由名称标准化匹配生成
 * （{@code Customer.normalizeName}，与客户唯一性校验同一口径），但**必须人工确认**——
 * 金蝶客户名与内部客户名常有全半角/后缀差异，全自动会错配。
 *
 * @author admin-system
 * @since 2026-11-03
 */
public interface KdCustomerMappingAppService {

    /**
     * 映射行列表（一个金蝶客户一行，含当前映射状态）。业务员只看本人，超管可跨人。
     *
     * @param acctId 账套过滤；空 = 全部账套
     * @param status 状态过滤：UNMATCHED / SUGGESTED / CONFIRMED；空 = 全部
     * @param keyword 金蝶客户名称/编码关键字；空 = 不过滤
     * @param ownerUserId 归属人过滤（仅管理员生效）
     * @return 映射行列表
     */
    List<KdCustomerMappingResp> list(String acctId, String status, String keyword, String ownerUserId);

    /**
     * 自动匹配建议：对未映射的金蝶客户，用名称标准化匹配唯一命中的内部客户，
     * 生成 AUTO_SUGGEST 行（不覆盖已确认的 MANUAL 行）。
     *
     * @param acctId 账套；空 = 全部账套
     * @param ownerUserId 归属人过滤（仅管理员生效）
     * @return 建议数与跳过数（歧义/重名命中不生成建议）
     */
    AutoSuggestResult autoSuggest(String acctId, String ownerUserId);

    /**
     * 人工保存映射（视为已确认）。已有建议行则覆盖升级为 MANUAL。
     *
     * @param req 请求
     */
    void save(KdMappingSaveReq req);

    /**
     * 自动建议结果。
     *
     * @param suggested 生成建议数
     * @param skipped 跳过数（无命中或歧义命中）
     */
    record AutoSuggestResult(int suggested, int skipped) {
    }
}
