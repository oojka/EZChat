import { type FrontendError, ErrorType, ErrorSeverity, createFrontendError, isFrontendError } from './ErrorType';
import { ElMessage, ElNotification } from 'element-plus';
import i18n from '@/i18n';
import router from '@/router';

const { t } = i18n.global;

/**
 * 错误处理器配置
 */
export interface ErrorHandlerConfig {
  /** 是否启用错误通知 */
  enableNotification: boolean;
  /** 是否启用控制台日志 */
  enableConsoleLog: boolean;
  /** 是否启用错误上报 */
  enableErrorReporting: boolean;
  /** 错误上报端点 */
  reportingEndpoint?: string;
  /** 需要忽略的错误类型 */
  ignoredErrorTypes: ErrorType[];
  /** 需要自动重试的错误类型 */
  retryableErrorTypes: ErrorType[];
  /** 最大重试次数 */
  maxRetryCount: number;
}

/**
 * 默认错误处理器配置
 */
const DEFAULT_CONFIG: ErrorHandlerConfig = {
  enableNotification: true,
  enableConsoleLog: true,
  enableErrorReporting: false,
  ignoredErrorTypes: [
    ErrorType.VALIDATION_REQUIRED_ERROR,
    ErrorType.VALIDATION_FORMAT_ERROR,
    ErrorType.VALIDATION_LENGTH_ERROR,
    ErrorType.VALIDATION_RANGE_ERROR,
  ],
  retryableErrorTypes: [
    ErrorType.ASYNC_PROMISE_REJECTION,
    ErrorType.ASYNC_AWAIT_ERROR,
    ErrorType.STATE_STORE_ACTION_ERROR,
  ],
  maxRetryCount: 3,
};

/**
 * 错误处理器类
 */
class ErrorHandler {
  private config: ErrorHandlerConfig;
  private errorQueue: FrontendError[] = [];
  private maxQueueSize = 100;
  private isReporting = false;

  constructor(config: Partial<ErrorHandlerConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.setupGlobalErrorHandlers();
  }

  /**
   * 设置全局错误处理器
   */
  private setupGlobalErrorHandlers(): void {
    // 全局未捕获的Promise错误
    window.addEventListener('unhandledrejection', (event) => {
      this.handleError(
        createFrontendError(
          ErrorType.ASYNC_PROMISE_REJECTION,
          `Unhandled promise rejection: ${event.reason}`,
          ErrorSeverity.ERROR,
          event.reason
        )
      );
    });

    // 全局错误事件
    window.addEventListener('error', (event) => {
      const error = createFrontendError(
        ErrorType.UNKNOWN_ERROR,
        `Global error: ${event.message}`,
        ErrorSeverity.ERROR,
        event.error,
        {
          filePath: event.filename,
          lineNumber: event.lineno,
          columnNumber: event.colno,
        }
      );
      this.handleError(error);
    });

    // Vue应用错误处理器（需要在main.ts中注册）
    this.setupVueErrorHandler();
  }

  /**
   * 设置Vue错误处理器
   */
  private setupVueErrorHandler(): void {
    // 这个方法需要在main.ts中调用
    // 示例：app.config.errorHandler = errorHandler.handleVueError.bind(errorHandler);
  }

  /**
   * 处理Vue错误
   */
  public handleVueError(err: unknown, instance: any, info: string): void {
    const error = createFrontendError(
      ErrorType.COMPONENT_RENDER_ERROR,
      `Vue error in ${info}: ${err}`,
      ErrorSeverity.ERROR,
      err,
      {
        component: instance?.$options?.name || 'Unknown',
        userAction: info,
      }
    );
    this.handleError(error);
  }

  /**
   * 处理错误
   */
  public handleError(error: FrontendError | Error | string, context?: any): void {
    // 标准化错误对象
    const frontendError = this.normalizeError(error, context);

    // 检查是否应该忽略此错误
    if (this.shouldIgnoreError(frontendError)) {
      return;
    }

    // 添加到错误队列
    this.addToErrorQueue(frontendError);

    // 根据严重级别处理错误
    this.processErrorBySeverity(frontendError);

    // 记录到控制台
    if (this.config.enableConsoleLog) {
      this.logToConsole(frontendError);
    }

    // 发送通知
    if (this.config.enableNotification) {
      this.showNotification(frontendError);
    }

    // 错误上报
    if (this.config.enableErrorReporting && this.config.reportingEndpoint) {
      this.reportError(frontendError);
    }
  }

  /**
   * 标准化错误对象
   */
  private normalizeError(error: FrontendError | Error | string, context?: any): FrontendError {
    if (isFrontendError(error)) {
      return error;
    }

    if (error instanceof Error) {
      return createFrontendError(
        ErrorType.UNKNOWN_ERROR,
        error.message,
        ErrorSeverity.ERROR,
        error,
        context
      );
    }

    return createFrontendError(
      ErrorType.UNKNOWN_ERROR,
      String(error),
      ErrorSeverity.ERROR,
      undefined,
      context
    );
  }

  /**
   * 检查是否应该忽略此错误
   */
  private shouldIgnoreError(error: FrontendError): boolean {
    return this.config.ignoredErrorTypes.includes(error.type);
  }

  /**
   * 添加到错误队列
   */
  private addToErrorQueue(error: FrontendError): void {
    this.errorQueue.push(error);
    
    // 限制队列大小
    if (this.errorQueue.length > this.maxQueueSize) {
      this.errorQueue.shift();
    }
  }

  /**
   * 根据严重级别处理错误
   */
  private processErrorBySeverity(error: FrontendError): void {
    switch (error.severity) {
      case ErrorSeverity.FATAL:
        this.handleFatalError(error);
        break;
      case ErrorSeverity.CRITICAL:
        this.handleCriticalError(error);
        break;
      case ErrorSeverity.ERROR:
        this.handleErrorLevel(error);
        break;
      case ErrorSeverity.WARNING:
        this.handleWarning(error);
        break;
      case ErrorSeverity.INFO:
        // 信息级别错误只记录，不特殊处理
        break;
    }
  }

  /**
   * 处理致命错误
   */
  private handleFatalError(error: FrontendError): void {
    // 致命错误：应用无法继续运行
    console.error('🚨 FATAL ERROR:', error);
    
    // 显示错误页面
    router.replace({
      path: '/error',
      state: {
        code: 'FATAL',
        message: t('error.fatal_error'),
        details: error.message,
      },
    }).catch(() => {});
  }

  /**
   * 处理严重错误
   */
  private handleCriticalError(error: FrontendError): void {
    // 严重错误：功能无法使用
    console.error('🔴 CRITICAL ERROR:', error);
    
    // 显示错误提示
    ElMessage.error({
      message: t('error.critical_error'),
      duration: 5000,
      showClose: true,
    });
  }

  /**
   * 处理一般错误
   */
  private handleErrorLevel(error: FrontendError): void {
    // 一般错误：功能部分受影响
    console.error('❌ ERROR:', error);
  }

  /**
   * 处理警告
   */
  private handleWarning(error: FrontendError): void {
    // 警告：不影响功能使用
    console.warn('⚠️ WARNING:', error);
  }

  /**
   * 记录到控制台
   */
  private logToConsole(error: FrontendError): void {
    const logEntry = {
      type: error.type,
      severity: error.severity,
      message: error.message,
      timestamp: error.timestamp.toISOString(),
      component: error.component,
      stack: error.stack,
      context: {
        userAction: error.userAction,
        appState: error.appState,
      },
    };

    switch (error.severity) {
      case ErrorSeverity.FATAL:
      case ErrorSeverity.CRITICAL:
      case ErrorSeverity.ERROR:
        console.error('Frontend Error:', logEntry);
        break;
      case ErrorSeverity.WARNING:
        console.warn('Frontend Warning:', logEntry);
        break;
      case ErrorSeverity.INFO:
        console.info('Frontend Info:', logEntry);
        break;
    }
  }

  /**
   * 显示通知
   */
  private showNotification(error: FrontendError): void {
    // 根据错误类型选择不同的通知方式
    switch (error.severity) {
      case ErrorSeverity.FATAL:
      case ErrorSeverity.CRITICAL:
        ElNotification.error({
          title: t('error.notification_title'),
          message: error.message,
          duration: 0, // 不自动关闭
          showClose: true,
        });
        break;
      case ErrorSeverity.ERROR:
        ElMessage.error({
          message: error.message,
          duration: 3000,
          showClose: true,
        });
        break;
      case ErrorSeverity.WARNING:
        ElMessage.warning({
          message: error.message,
          duration: 2000,
          showClose: true,
        });
        break;
      case ErrorSeverity.INFO:
        ElMessage.info({
          message: error.message,
          duration: 1500,
          showClose: true,
        });
        break;
    }
  }

  /**
   * 上报错误到服务器
   */
  private async reportError(error: FrontendError): Promise<void> {
    if (this.isReporting || !this.config.reportingEndpoint) {
      return;
    }

    this.isReporting = true;
    try {
      const reportData = {
        type: error.type,
        severity: error.severity,
        message: error.message,
        timestamp: error.timestamp.toISOString(),
        component: error.component,
        filePath: error.filePath,
        lineNumber: error.lineNumber,
        columnNumber: error.columnNumber,
        userAgent: navigator.userAgent,
        url: window.location.href,
        stack: error.stack,
        userAction: error.userAction,
        appState: error.appState,
      };

      await fetch(this.config.reportingEndpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(reportData),
      });
    } catch (reportError) {
      console.error('Failed to report error:', reportError);
    } finally {
      this.isReporting = false;
    }
  }

  /**
   * 获取错误队列
   */
  public getErrorQueue(): FrontendError[] {
    return [...this.errorQueue];
  }

  /**
   * 清空错误队列
   */
  public clearErrorQueue(): void {
    this.errorQueue = [];
  }

  /**
   * 更新配置
   */
  public updateConfig(newConfig: Partial<ErrorHandlerConfig>): void {
    this.config = { ...this.config, ...newConfig };
  }

  /**
   * 获取当前配置
   */
  public getConfig(): ErrorHandlerConfig {
    return { ...this.config };
  }

  /**
   * 创建特定类型的错误
   */
  public createAndHandle(
    type: ErrorType,
    message: string,
    severity: ErrorSeverity = ErrorSeverity.ERROR,
    originalError?: any,
    context?: any
  ): void {
    const error = createFrontendError(type, message, severity, originalError, context);
    this.handleError(error);
  }
}

// 创建默认的错误处理器实例
export const errorHandler = new ErrorHandler();

// 导出便捷方法
export const handleError = errorHandler.handleError.bind(errorHandler);
export const handleVueError = errorHandler.handleVueError.bind(errorHandler);
export const createAndHandle = errorHandler.createAndHandle.bind(errorHandler);

export default errorHandler;
