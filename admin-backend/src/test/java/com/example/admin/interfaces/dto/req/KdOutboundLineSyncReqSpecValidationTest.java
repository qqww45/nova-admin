package com.example.admin.interfaces.dto.req;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 出库分录上行规格字段长度校验测试。
 *
 * <p>回归背景：金蝶 FSpecification 是可配置长度的文本字段，真实账套存在超 300 字的
 * 规格。2026-11-03 起客户端 1.2.1 全链路采集规格后，一个超长规格会让整批上行被
 * Bean Validation 拒绝（生产批次失败信息：{@code rows[472].spec: size must be
 * between 0 and 300}），批次反复重试、水位无法推进、同步整体不可用。
 *
 * <p>口径：规格是对账依据，禁止截断——DTO 上限与 kd_outbound_line.spec 列宽
 * （V22）一起放宽到 1000，超限行仍应被明确拒绝。
 *
 * @author admin-system
 * @since 2026-11-03
 */
class KdOutboundLineSyncReqSpecValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = jakarta.validation.Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static KdOutboundLineSyncReq.Row row(String spec) {
        KdOutboundLineSyncReq.Row row = new KdOutboundLineSyncReq.Row();
        row.setOutboundFid(1001L);
        row.setSpec(spec);
        return row;
    }

    private static KdOutboundLineSyncReq req(KdOutboundLineSyncReq.Row... rows) {
        KdOutboundLineSyncReq req = new KdOutboundLineSyncReq();
        req.setBatchId("batch-1");
        req.setRows(List.of(rows));
        return req;
    }

    @Test
    @DisplayName("301 字规格应放行——此前正是它以 size must be between 0 and 300 拒掉整批")
    void oversizeSpecBeyondOldLimitShouldPass() {
        String spec = "规".repeat(301);

        Set<ConstraintViolation<KdOutboundLineSyncReq.Row>> violations = validator.validate(row(spec));

        assertTrue(violations.isEmpty(), "301 字规格必须放行，违规项：" + violations);
    }

    @Test
    @DisplayName("1000 字为上限：1000 放行，1001 拒绝并给出明确提示")
    void specBoundaryShouldBe1000() {
        assertTrue(validator.validate(row("规".repeat(1000))).isEmpty(),
                "恰好 1000 字的规格应放行");

        Set<ConstraintViolation<KdOutboundLineSyncReq.Row>> violations = validator.validate(row("规".repeat(1001)));
        assertEquals(1, violations.size(), "超限应只报规格一个违规项");
        ConstraintViolation<KdOutboundLineSyncReq.Row> violation = violations.iterator().next();
        assertEquals("spec", violation.getPropertyPath().toString(), "违规字段应为 spec");
        assertEquals("规格长度不能超过 1000 字", violation.getMessage());
    }

    @Test
    @DisplayName("整批校验应定位到具体行——错误路径形如 rows[472].spec")
    void requestLevelValidationShouldPointToOffendingRow() {
        KdOutboundLineSyncReq req = req(row("规格A"), row("规格B"), row("规".repeat(1001)));

        Set<ConstraintViolation<KdOutboundLineSyncReq>> violations = validator.validate(req);

        assertEquals(1, violations.size());
        ConstraintViolation<KdOutboundLineSyncReq> violation = violations.iterator().next();
        assertEquals("rows[2].spec", violation.getPropertyPath().toString(),
                "错误路径应能定位到具体行，便于排查是哪一条分录");
    }

    @Test
    @DisplayName("规格为空 / 为 null 仍应放行（账套取不到规格时该列留空）")
    void blankOrNullSpecShouldPass() {
        assertFalse(validator.validate(row("")).iterator().hasNext(), "空串应放行");
        assertFalse(validator.validate(row(null)).iterator().hasNext(), "null 应放行");
    }
}
