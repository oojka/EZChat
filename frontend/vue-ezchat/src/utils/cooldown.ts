/**
 * 高级频率限制器：支持窗口期次数限制及超限后的封锁时间
 */
export class Cooldown {
  private duration: number
  private maxAttempts: number
  private lockout: number

  private attempts: number = 0
  private windowStart: number = 0
  private lockoutEnd: number = 0

  /**
   * @param durationMs 统计窗口时间（毫秒）
   * @param maxAttempts 窗口内允许的最大次数
   * @param lockoutMs 触发限制后的封锁时间（毫秒）
   */
  constructor(durationMs: number = 3000, maxAttempts: number = 10, lockoutMs: number = 15000) {
    this.duration = durationMs
    this.maxAttempts = maxAttempts
    this.lockout = lockoutMs
  }

  /**
   * 尝试执行操作
   *
   * 业务目的：对高频操作做“窗口期限流 + 超限封锁”，避免误触/恶意刷接口导致后端压力或 UI 失控。
   *
   * @returns boolean 是否允许执行
   */
  canExecute(): boolean {
    const now = Date.now()

    // 1. 检查是否处于封锁期
    if (now < this.lockoutEnd) {
      return false
    }

    // 2. 检查窗口期是否已过
    if (now - this.windowStart > this.duration) {
      // 开启新窗口
      this.windowStart = now
      this.attempts = 1
      return true
    }

    // 3. 窗口期内，增加计数
    this.attempts++
    if (this.attempts > this.maxAttempts) {
      // 触发封锁
      this.lockoutEnd = now + this.lockout
      return false
    }

    return true
  }

  /**
   * 获取需要等待的剩余时间（秒）
   *
   * 业务目的：用于 UI 提示“还需等待 X 秒”。
   */
  getRemainingTime(): number {
    const now = Date.now()
    const waitTime = Math.max(this.lockoutEnd - now, 0)
    return Math.ceil(waitTime / 1000)
  }

  /**
   * 重置所有状态
   *
   * 业务目的：在用户明确完成/取消某些操作后，允许重新计数。
   */
  reset() {
    this.attempts = 0
    this.windowStart = 0
    this.lockoutEnd = 0
  }
}
