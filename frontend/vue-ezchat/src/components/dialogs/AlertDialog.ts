import { ElMessageBox } from 'element-plus'
import i18n from '@/i18n'

export type AlertDialogType =
  | 'success'
  | 'warning'
  | 'info'
  | 'error'
  | 'primary'
  | 'danger'

const { t } = i18n.global

export type AlertDialogOptions = {
  title?: string
  message: string
  confirmText?: string
  type?: AlertDialogType
  onConfirm?: () => void

  /**
   * 是否返回 Promise（用于 await）
   * @default true
   */
  isAwait?: boolean
}

/**
 * 根据类型获取默认标题
 */
const getDefaultTitleByType = (type: AlertDialogType): string => {
  switch (type) {
    case 'success':
      return t('dialog.success') || 'Success'
    case 'warning':
      return t('dialog.warning') || 'Warning'
    case 'error':
    case 'danger':
      return t('dialog.error') || 'Error'
    case 'primary':
    case 'info':
    default:
      return t('dialog.info') || 'Info'
  }
}

/**
 * Alert Dialog
 *
 * - 默认支持 await
 * - isAwait=false 时可作为纯提示使用
 */
export const showAlertDialog = ({
  title,
  message,
  confirmText,
  type = 'info',
  onConfirm,
  isAwait = true,
}: AlertDialogOptions): Promise<void> | void => {
  const finalTitle = title ? title : getDefaultTitleByType(type)
  const finalMessage = message
  const finalConfirmText = confirmText ? confirmText : t('common.confirm') || 'Confirm'

  // 1️⃣ 按钮样式：除了 error/danger 用红色，其他统一用系统蓝色
  let confirmButtonClass = 'ez-dialog-btn'
  if (type === 'danger' || type === 'error') {
    confirmButtonClass += ' el-button--danger'
  } else {
    confirmButtonClass += ' el-button--primary'
  }

  // 2️⃣ icon 类型
  let messageBoxType: 'success' | 'warning' | 'info' | 'error' = 'info'
  if (type === 'success') messageBoxType = 'success'
  if (type === 'warning') messageBoxType = 'warning'
  if (type === 'error' || type === 'danger') messageBoxType = 'error'

  // 3️⃣ 弹窗 Promise
  const promise = ElMessageBox.alert(finalMessage, finalTitle, {
    confirmButtonText: finalConfirmText,
    type: messageBoxType,
    confirmButtonClass,
    customClass: 'ez-dialog',
    center: false,
    showClose: false,
    closeOnClickModal: false,
    closeOnPressEscape: true,
  }).then(() => {
    onConfirm?.()
  })

  // 4️⃣ 是否返回 Promise
  if (isAwait) {
    return promise
  }
}
