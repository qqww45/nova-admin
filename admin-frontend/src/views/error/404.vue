<script setup lang="ts">
/**
 * 404 页面。
 *
 * <p>设计取向：与登录页共享同一套"几何装饰 + 品牌印记"语汇，
 * 让用户在遇到 404 时仍能感受到"这是同一个产品"，而不是通用错误页。
 *
 * <p>两个有意思的小细节：
 * <ul>
 *     <li>右上角放一个偏冷的 NOVA 渐变圈，模拟"页面进入了一个不知名星区"；</li>
 *     <li>数字 404 末位的 0 切到 0 旋转 90° → 视觉上"卡住了"，呼应"找不到"。</li>
 * </ul>
 */
import { useRouter } from 'vue-router';
import BrandMark from '@/components/BrandMark.vue';
import { BRAND_NAME } from '@/config/brand';

const router = useRouter();
</script>

<template>
  <div class="not-found page-container">
    <!-- 装饰：右上角 NOVA 渐变圈（与登录页同源） -->
    <div class="not-found__orb" aria-hidden="true" />
    <div class="not-found__grid" aria-hidden="true" />

    <BrandMark :size="48" class="not-found__mark" />

    <!-- 数字 404：用等宽字形 + 大字距，让"4"和"4"之间留出呼吸位，
         末位 0 旋转 90° 形成"卡住"的视觉隐喻 -->
    <p class="not-found__code">
      <span>4</span><span>0</span><span class="not-found__code-tilt">0</span>
    </p>
    <h1 class="not-found__title">未找到此页面</h1>
    <p class="not-found__desc">你访问的地址不存在、已被移除，或者暂时不可达。</p>
    <div class="not-found__actions">
      <el-button type="primary" size="large" @click="router.replace('/dashboard')">返回首页</el-button>
      <el-button size="large" @click="router.back()">返回上一页</el-button>
    </div>
    <p class="not-found__brand">{{ BRAND_NAME }}</p>
  </div>
</template>

<style scoped lang="scss">
/* 居中：垂直水平都居中，给大字号 404 留出足够舞台 */
.not-found {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - var(--admin-header-height) - var(--admin-tagsview-height));
  text-align: center;
  overflow: hidden;

  /* 右上角渐变圈：与登录页同源，加一层"页面在星空中迷失"的氛围 */
  &__orb {
    position: absolute;
    top: -120px;
    right: -120px;
    width: 360px;
    height: 360px;
    background: radial-gradient(circle at 30% 30%, rgba(37, 99, 235, 0.16), transparent 60%);
    border-radius: 50%;
    filter: blur(20px);
    pointer-events: none;
  }

  /* 网格底纹：与登录页一致，呼应"信息系统"的语境 */
  &__grid {
    position: absolute;
    inset: 0;
    pointer-events: none;
    opacity: 0.4;
    background-image:
      linear-gradient(var(--admin-border-color) 1px, transparent 1px),
      linear-gradient(90deg, var(--admin-border-color) 1px, transparent 1px);
    background-size: 48px 48px;
    mask-image: radial-gradient(ellipse at 50% 50%, #000 10%, transparent 65%);
    -webkit-mask-image: radial-gradient(ellipse at 50% 50%, #000 10%, transparent 65%);
  }

  /* mark 浮在 404 数字之上，作为品牌锚点 */
  &__mark {
    margin-bottom: 18px;
  }

  /* 404 大数字：等宽字形 + 字距，让 4 0 0 之间留出呼吸位 */
  &__code {
    margin: 0 0 12px;
    font-size: 128px;
    font-weight: 700;
    line-height: 1;
    color: var(--admin-color-primary);
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.04em;
  }

  /* 末位 0 旋转 90°：视觉隐喻——"卡住了 / 找不到" */
  &__code-tilt {
    display: inline-block;
    transform: rotate(90deg);
    transform-origin: center;
  }

  &__title {
    margin: 0 0 8px;
    font-size: 22px;
    font-weight: 600;
    color: var(--admin-text-primary);
  }

  &__desc {
    max-width: 480px;
    margin: 0 0 28px;
    font-size: 14px;
    line-height: 1.7;
    color: var(--admin-text-regular);
  }

  &__actions {
    display: flex;
    gap: 12px;
  }

  &__brand {
    position: absolute;
    bottom: 32px;
    margin: 0;
    font-size: 12px;
    font-weight: 500;
    letter-spacing: 0.32em;
    color: var(--admin-text-regular);
    opacity: 0.6;
  }
}
</style>
