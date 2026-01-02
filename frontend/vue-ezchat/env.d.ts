/// <reference types="vite/client" />

/**
 * Vite 环境变量类型声明
 *
 * 说明：
 * - Vite 会把环境变量注入到 import.meta.env
 * - 这里仅补充本项目用到的自定义变量，避免 TS 提示不友好
 */
interface ImportMetaEnv {
  /**
   * 是否启用前端图片预处理（预压缩）
   * - 'false'：关闭
   * - 其他：开启（默认）
   */
  readonly VITE_ENABLE_CLIENT_IMAGE_PREPROCESS?: string

  /**
   * 前端 JPEG 质量（0~1）
   * 默认 0.9
   */
  readonly VITE_CLIENT_IMAGE_JPEG_QUALITY?: string

  /**
   * 是否允许把 PNG 转为 JPEG（可能导致截图/文字边缘轻微发糊）
   * - 'true'：允许（仅在超过阈值时才会转）
   * - 其他：不允许（默认）
   */
  readonly VITE_CLIENT_PNG_TO_JPEG?: string

  /**
   * PNG 转 JPEG 的触发阈值（MB）
   * 默认 3
   */
  readonly VITE_CLIENT_PNG_TO_JPEG_THRESHOLD_MB?: string
}

