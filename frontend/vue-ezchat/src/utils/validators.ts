/**
 * =========================================
 * 密码校验等级说明 (PasswordSecurityLevel)
 * =========================================
 * 1. basic:        基础等级。仅限半角可见字符 (ASCII 33-126)，禁止全角字符和空格。
 * 2. alphanumeric: 中级等级。在 basic 基础上，必须同时包含 [字母] 和 [数字]。
 * 3. strong:       高级等级。在 basic 基础上，必须同时包含 [大写字母]、[小写字母] 和 [数字]。
 * 4. complex:      专家等级。在 basic 基础上，必须同时包含 [大写字母]、[小写字母]、[数字] 和 [特殊符号]。
 */

/**
 * =========================================
 * 核心校验规则 (正则表达式)
 * =========================================
 */

/**
 * 用户名规则：
 * 1. 必须以字母开头 ^[a-zA-Z]
 * 2. 允许包含字母、数字、点(.)、下划线(_)、短横线(-) [a-zA-Z0-9._-]
 * 3. 长度限制为 2-20 位 {1,19}$
 */
export const USERNAME_REG = /^[a-zA-Z][a-zA-Z0-9._-]{1,19}$/

/**
 * 基础密码规则 (半角可见字符)：
 * 匹配 ASCII 33-126 范围内的所有字符，禁止全角和空格
 */
const PWD_BASE_CHAR = '[\\x21-\\x7e]'

/** 必须包含字母和数字的断言 */
const LOOKAHEAD_ALPHANUMERIC = '(?=.*[a-zA-Z])(?=.*[0-9])'

/** 必须包含大写、小写、数字的断言 */
const LOOKAHEAD_STRONG = '(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])'

/** 必须包含大写、小写、数字、特殊符号的断言 */
const LOOKAHEAD_COMPLEX = '(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[\\x21-\\x2f\\x3a-\\x40\\x5b-\\x60\\x7b-\\x7e])'

/**
 * =========================================
 * 类型定义与工具函数
 * =========================================
 */

export type PasswordSecurityLevel = 'basic' | 'alphanumeric' | 'strong' | 'complex'

type PasswordOptions = {
  min?: number
  max?: number
  level?: PasswordSecurityLevel
}

/**
 * 动态生成密码正则表达式
 *
 * 业务目的：不同页面/不同场景可以配置不同强度（例如注册更强、加入房间可稍弱）。
 *
 * @param options 校验配置 (min, max, level)
 */
export const getPasswordReg = (options: PasswordOptions = {}): RegExp => {
  const { min = 8, max = 20, level = 'basic' } = options

  // 构建长度限制部分
  const lengthConstraint = `${PWD_BASE_CHAR}{${min},${max}}`

  let pattern = '^'
  switch (level) {
    case 'alphanumeric':
      pattern += LOOKAHEAD_ALPHANUMERIC
      break
    case 'strong':
      pattern += LOOKAHEAD_STRONG
      break
    case 'complex':
      pattern += LOOKAHEAD_COMPLEX
      break
    case 'basic':
    default:
      break
  }
  pattern += `${lengthConstraint}$`

  return new RegExp(pattern)
}

/**
 * 校验用户名 (内置非空检查)
 *
 * @param val 输入值
 */
export const isValidUsername = (val: unknown): boolean => {
  if (typeof val !== 'string') return false
  return USERNAME_REG.test(val)
}

/**
 * 校验密码 (内置非空检查)
 *
 * @param val 输入值
 * @param options 强度配置
 */
export const isValidPassword = (val: unknown, options?: PasswordOptions): boolean => {
  if (typeof val !== 'string') return false
  return getPasswordReg(options).test(val)
}
