/**
 * 前端异常类型枚举
 * 
 * 分类说明：
 * 1. COMPONENT_* - 组件相关错误
 * 2. EVENT_* - 事件处理相关错误
 * 3. ASYNC_* - 异步操作相关错误
 * 4. STATE_* - 状态管理相关错误
 * 5. ROUTER_* - 路由相关错误
 * 6. DOM_* - DOM操作相关错误
 * 7. VALIDATION_* - 表单验证相关错误
 * 8. UI_* - 用户界面相关错误
 */

export enum ErrorType {
  // ========== 组件相关错误 ==========
  /** 组件渲染错误 */
  COMPONENT_RENDER_ERROR = 'COMPONENT_RENDER_ERROR',
  /** 组件挂载错误 */
  COMPONENT_MOUNT_ERROR = 'COMPONENT_MOUNT_ERROR',
  /** 组件更新错误 */
  COMPONENT_UPDATE_ERROR = 'COMPONENT_UPDATE_ERROR',
  /** 组件卸载错误 */
  COMPONENT_UNMOUNT_ERROR = 'COMPONENT_UNMOUNT_ERROR',
  /** Props验证失败 */
  COMPONENT_PROPS_VALIDATION_ERROR = 'COMPONENT_PROPS_VALIDATION_ERROR',

  // ========== 事件处理错误 ==========
  /** 事件处理器执行错误 */
  EVENT_HANDLER_ERROR = 'EVENT_HANDLER_ERROR',
  /** 表单提交错误 */
  EVENT_FORM_SUBMIT_ERROR = 'EVENT_FORM_SUBMIT_ERROR',
  /** 按钮点击错误 */
  EVENT_BUTTON_CLICK_ERROR = 'EVENT_BUTTON_CLICK_ERROR',
  /** 输入验证错误 */
  EVENT_INPUT_VALIDATION_ERROR = 'EVENT_INPUT_VALIDATION_ERROR',

  // ========== 异步操作错误 ==========
  /** Promise拒绝错误 */
  ASYNC_PROMISE_REJECTION = 'ASYNC_PROMISE_REJECTION',
  /** async/await执行错误 */
  ASYNC_AWAIT_ERROR = 'ASYNC_AWAIT_ERROR',
  /** 定时器错误 */
  ASYNC_TIMER_ERROR = 'ASYNC_TIMER_ERROR',
  /** 动画帧错误 */
  ASYNC_ANIMATION_FRAME_ERROR = 'ASYNC_ANIMATION_FRAME_ERROR',

  // ========== 状态管理错误 ==========
  /** Store状态更新错误 */
  STATE_STORE_UPDATE_ERROR = 'STATE_STORE_UPDATE_ERROR',
  /** Store Action执行错误 */
  STATE_STORE_ACTION_ERROR = 'STATE_STORE_ACTION_ERROR',
  /** Store Getter计算错误 */
  STATE_STORE_GETTER_ERROR = 'STATE_STORE_GETTER_ERROR',
  /** 状态同步错误 */
  STATE_SYNC_ERROR = 'STATE_SYNC_ERROR',

  // ========== 路由相关错误 ==========
  /** 路由导航错误 */
  ROUTER_NAVIGATION_ERROR = 'ROUTER_NAVIGATION_ERROR',
  /** 路由守卫错误 */
  ROUTER_GUARD_ERROR = 'ROUTER_GUARD_ERROR',
  /** 路由参数解析错误 */
  ROUTER_PARAM_PARSE_ERROR = 'ROUTER_PARAM_PARSE_ERROR',
  /** 路由组件加载错误 */
  ROUTER_COMPONENT_LOAD_ERROR = 'ROUTER_COMPONENT_LOAD_ERROR',

  // ========== DOM操作错误 ==========
  /** 元素引用错误 */
  DOM_ELEMENT_REF_ERROR = 'DOM_ELEMENT_REF_ERROR',
  /** DOM操作错误 */
  DOM_MANIPULATION_ERROR = 'DOM_MANIPULATION_ERROR',
  /** 样式操作错误 */
  DOM_STYLE_ERROR = 'DOM_STYLE_ERROR',
  /** 属性操作错误 */
  DOM_ATTRIBUTE_ERROR = 'DOM_ATTRIBUTE_ERROR',

  // ========== 表单验证错误 ==========
  /** 必填字段验证失败 */
  VALIDATION_REQUIRED_ERROR = 'VALIDATION_REQUIRED_ERROR',
  /** 格式验证失败 */
  VALIDATION_FORMAT_ERROR = 'VALIDATION_FORMAT_ERROR',
  /** 长度验证失败 */
  VALIDATION_LENGTH_ERROR = 'VALIDATION_LENGTH_ERROR',
  /** 范围验证失败 */
  VALIDATION_RANGE_ERROR = 'VALIDATION_RANGE_ERROR',
  /** 自定义验证失败 */
  VALIDATION_CUSTOM_ERROR = 'VALIDATION_CUSTOM_ERROR',

  // ========== 用户界面错误 ==========
  /** 模态框操作错误 */
  UI_MODAL_ERROR = 'UI_MODAL_ERROR',
  /** 通知/消息错误 */
  UI_NOTIFICATION_ERROR = 'UI_NOTIFICATION_ERROR',
  /** 加载状态错误 */
  UI_LOADING_ERROR = 'UI_LOADING_ERROR',
  /** 主题切换错误 */
  UI_THEME_ERROR = 'UI_THEME_ERROR',

  // ========== 其他错误 ==========
  /** 未知错误 */
  UNKNOWN_ERROR = 'UNKNOWN_ERROR',
  /** 配置错误 */
  CONFIGURATION_ERROR = 'CONFIGURATION_ERROR',
  /** 环境错误 */
  ENVIRONMENT_ERROR = 'ENVIRONMENT_ERROR',
  /** 第三方库错误 */
  THIRD_PARTY_LIB_ERROR = 'THIRD_PARTY_LIB_ERROR',
}

/**
 * 错误严重级别
 */
export enum ErrorSeverity {
  /** 致命错误 - 应用无法继续运行 */
  FATAL = 'FATAL',
  /** 严重错误 - 功能无法使用 */
  CRITICAL = 'CRITICAL',
  /** 一般错误 - 功能部分受影响 */
  ERROR = 'ERROR',
  /** 警告 - 不影响功能使用 */
  WARNING = 'WARNING',
  /** 信息 - 仅用于记录 */
  INFO = 'INFO',
}

/**
 * 前端错误接口
 */
export interface FrontendError {
  /** 错误类型 */
  type: ErrorType;
  /** 错误严重级别 */
  severity: ErrorSeverity;
  /** 错误消息 */
  message: string;
  /** 错误堆栈 */
  stack?: string;
  /** 错误发生时间 */
  timestamp: Date;
  /** 错误发生的组件/模块 */
  component?: string;
  /** 错误发生的文件路径 */
  filePath?: string;
  /** 错误发生的行号 */
  lineNumber?: number;
  /** 错误发生的列号 */
  columnNumber?: number;
  /** 用户操作上下文 */
  userAction?: string;
  /** 应用状态上下文 */
  appState?: Record<string, any>;
  /** 原始错误对象 */
  originalError?: any;
}

/**
 * 创建前端错误对象
 */
export function createFrontendError(
  type: ErrorType,
  message: string,
  severity: ErrorSeverity = ErrorSeverity.ERROR,
  originalError?: any,
  context?: {
    component?: string;
    filePath?: string;
    lineNumber?: number;
    columnNumber?: number;
    userAction?: string;
    appState?: Record<string, any>;
  }
): FrontendError {
  const error: FrontendError = {
    type,
    severity,
    message,
    timestamp: new Date(),
    originalError,
  };

  // 如果有堆栈信息，提取并存储
  if (originalError?.stack) {
    error.stack = originalError.stack;
  } else if (new Error().stack) {
    error.stack = new Error().stack;
  }

  // 添加上下文信息
  if (context) {
    Object.assign(error, context);
  }

  return error;
}

/**
 * 判断是否为前端错误对象
 */
export function isFrontendError(error: any): error is FrontendError {
  return (
    error &&
    typeof error === 'object' &&
    'type' in error &&
    'severity' in error &&
    'message' in error &&
    'timestamp' in error
  );
}
