package com.example.admin.domain.service;

import com.example.admin.domain.model.auth.SlideSample;

/**
 * 轻验证判定（领域服务接口）：判断一次滑块拖动是否像"人"完成的。
 *
 * <p>为什么不再用"看清了什么"来考用户：
 * 图形验证码的答案客观存在于图片里，因此可被 OCR 识别、也可被人工打码平台外包；
 * 这里判定的是<b>完成过程</b>（落点 + 轨迹），静态图像对通过没有任何直接作用。
 *
 * <p>为什么也不只看"最后对没对上"：
 * 位置正确只是结果，脚本可以先算出缺口坐标再模拟拖动。
 * 位置 + 轨迹两层叠加，才是"两道关"而不是"一道关"。
 *
 * <p>边界声明（对内对外都要说清）：
 * 这是"抬高自动化成本"的一层，不是不可绕过的银弹。
 * 真实浏览器 + 拟人轨迹的挑战依然可能通过，因此本层必须与
 * 环境识别、速率限制、失败计数、强验证通道叠加使用，而不是单独承担防御。
 *
 * @author admin-system
 * @since 2026-01-01
 */
public interface HumanInteractionVerifier {

    /**
     * 判定滑块样本是否通过。
     *
     * @param sample 滑块样本
     * @return true 表示通过
     */
    boolean verify(SlideSample sample);
}
