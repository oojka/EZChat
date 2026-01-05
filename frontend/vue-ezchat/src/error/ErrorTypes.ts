/**
 * ================================
 * 前端错误体系（中小型项目推荐版）
 * ================================
 *
 * 设计目标：
 * 1. 语义统一
 * 2. 使用成本低
 * 3. 可逐步扩展为企业级
 */

/**
 * 错误类型（精简但够用）
 */
export enum ErrorType {
  /** 网络 / 接口 / 异步错误 */
  NETWORK = 'NETWORK',

  /** 存储错误 */
  STORAGE = 'STORAGE',

  /** 表单 / 参数校验错误 */
  VALIDATION = 'VALIDATION',

  /** 路由 / 权限相关错误 */
  ROUTER = 'ROUTER',

  /** 状态管理错误（Pinia / Vuex） */
  STATE = 'STATE',

  /** UI 交互错误（弹窗 / loading / message） */
  UI = 'UI',

  /** 组件运行时错误 */
  COMPONENT = 'COMPONENT',

  /** 第三方库错误 */
  THIRD_PARTY = 'THIRD_PARTY',

  /** 未知 / 兜底错误 */
  UNKNOWN = 'UNKNOWN',
}

/**
 * 错误严重级别
 */
export enum ErrorSeverity {
  /** 页面或核心功能不可用 */
  FATAL = 'FATAL',

  /** 当前功能失败 */
  ERROR = 'ERROR',

  /** 不影响主流程 */
  WARNING = 'WARNING',
}

/**
 * 前端错误对象
 */
export interface AppError {
  /** 错误类型 */
  type: ErrorType

  /** 严重级别 */
  severity: ErrorSeverity

  /** 给开发者看的错误描述 */
  message: string

  /** 时间戳 */
  timestamp: number

  /** 发生错误的组件名 */
  component?: string

  /** 用户行为 / 操作语义 */
  action?: string

  /** 原始错误（用于调试） */
  originalError?: unknown
}

/**
 * 创建统一的前端错误对象
 *
 * @param type 错误类型（NETWORK / VALIDATION / UI 等）
 * @param message 错误描述（给开发者或日志用）
 * @param options 可选上下文信息
 *  - severity: 错误严重级别（默认 ERROR）
 *  - component: 发生错误的组件名
 *  - action: 触发错误的用户操作（如 login / submitForm）
 *  - originalError: 原始错误对象（如 catch(e) 中的 e）
 */
export function createAppError(
  type: ErrorType,
  message: string,
  options?: {
    severity?: ErrorSeverity
    component?: string
    action?: string
    originalError?: unknown
  }
): AppError {
  return {
    type,
    message,
    severity: options?.severity ?? ErrorSeverity.ERROR,
    component: options?.component,
    action: options?.action,
    originalError: options?.originalError,
    timestamp: Date.now(),
  }
}

/**
 * 类型守卫
 */
export function isAppError(error: unknown): error is AppError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'type' in error &&
    'severity' in error &&
    'message' in error &&
    'timestamp' in error
  )
}
