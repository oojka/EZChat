import {ElMessageBox} from 'element-plus'
import i18n from '@/i18n'

export type DialogType = 'success' | 'warning' | 'info' | 'error' | 'primary' | 'danger'

const { t } = i18n.global

/**
 * 智能翻译：如果是 i18n key 则翻译，否则原样返回
 */
const translateIfKey = (text: string): string => {
  const translated = t(text)
  return translated !== text ? translated : text
}

/**
 * 确认弹窗配置
 *
 * 业务目的：统一确认弹窗的样式与行为（确认/取消回调），避免页面各处重复写 ElMessageBox 配置。
 */
type ConfirmDialogOptions = {
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: DialogType
  onConfirm: () => void
  onCancel?: () => void
}

/**
 * 显示自定义风格的确认弹窗
 */
export const showConfirmDialog = ({
  title = 'dialog.confirm',
  message,
  confirmText = 'common.confirm',
  cancelText = 'common.cancel',
  type = 'warning',
  onConfirm,
  onCancel,
}: ConfirmDialogOptions) => {
  // 应用智能翻译
  const finalTitle = translateIfKey(title)
  const finalMessage = translateIfKey(message)
  const finalConfirmText = translateIfKey(confirmText)
  const finalCancelText = translateIfKey(cancelText)

  // 1) 映射 type 到 Element Plus 的按钮样式（用于统一自定义按钮外观）
  let confirmButtonClass = 'ez-dialog-btn'
  if (type === 'danger') {
    confirmButtonClass += ' el-button--danger'
  } else if (type === 'primary') {
    confirmButtonClass += ' el-button--primary'
  } else if (type === 'success') {
    confirmButtonClass += ' el-button--success'
  } else {
    confirmButtonClass += ' el-button--primary'
  }

  // 2) 映射 type 到 ElMessageBox 的 type(icon)，保证弹窗图标语义一致
  let messageBoxType: 'success' | 'warning' | 'info' | 'error' = 'warning'
  if (type === 'success') messageBoxType = 'success'
  if (type === 'error' || type === 'danger') messageBoxType = 'error'
  if (type === 'info') messageBoxType = 'info'
  if (type === 'primary') messageBoxType = 'info'

  // 3) 弹窗本体：确认走 onConfirm，取消走 onCancel（可选）
  ElMessageBox.confirm(finalMessage, finalTitle, {
    confirmButtonText: finalConfirmText,
    cancelButtonText: finalCancelText,
    type: messageBoxType,
    confirmButtonClass: confirmButtonClass,
    cancelButtonClass: 'ez-dialog-btn',
    customClass: 'ez-dialog',
    center: false,
    showClose: false,
  })
    .then(() => {
      onConfirm()
    })
    .catch(() => {
      if (onCancel) {
        onCancel()
      }
    })
}
