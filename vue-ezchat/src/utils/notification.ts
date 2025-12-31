import {h} from 'vue'
import {ElNotification} from 'element-plus'
import type {LoginUserInfo, Message, User} from '@/type'
import router from '@/router'
import i18n from '@/i18n' // 引入 i18n 实例

const { t } = i18n.global

const FONT_STACK = '"Inter", "SF Pro Display", "Helvetica Neue", Helvetica, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif'

const getPreviewContent = (message: Message): string => {
  let content = message.text || ''
  if (message.images && message.images.length > 0) {
    content += `[${t('chat.image')}]`.repeat(message.images.length)
  }
  return content || `[${t('chat.new_message')}]`
}

export const showMessageNotification = (message: Message, sender: User, chatName: string) => {
  const previewText = getPreviewContent(message)
  ElNotification({
    title: chatName || t('chat.new_message'),
    customClass: 'ez-notification info',
    offset: 70,
    position: 'top-right',
    duration: 4000,
    message: h('div', { style: `display: flex; align-items: flex-start; gap: 14px; cursor: pointer; font-family: ${FONT_STACK};` }, [
      h('img', { src: sender.avatar?.objectThumbUrl || '', style: 'width: 44px; height: 44px; border-radius: 12px; object-fit: cover; flex-shrink: 0; box-shadow: 0 4px 12px rgba(0,0,0,0.08);' }),
      h('div', { style: 'flex: 1; overflow: hidden; display: flex; flex-direction: column; gap: 2px;' }, [
        h('span', { style: 'font-size: 14px; font-weight: 700; color: #1e293b;' }, sender.nickname),
        h('span', { style: 'font-size: 13px; color: #64748b; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;' }, previewText),
      ]),
    ]),
    onClick: () => { router.push(`/chat/${message.chatCode}`).catch(() => {}) },
  })
}

export const showWelcomeNotification = (loginUserInfo: LoginUserInfo) => {
  ElNotification({
    title: t('auth.welcome_back'),
    customClass: 'ez-notification welcome',
    offset: 70,
    position: 'top-right',
    duration: 8000,
    message: h('div', { style: `display: flex; align-items: center; gap: 20px; padding: 4px 0; font-family: ${FONT_STACK};` }, [
      h('img', { src: loginUserInfo.avatar?.objectThumbUrl || loginUserInfo.avatar?.objectUrl || '', style: 'width: 64px; height: 64px; border-radius: 18px; object-fit: cover; border: 3px solid #fff; box-shadow: 0 8px 20px rgba(0,0,0,0.1); flex-shrink: 0;' }),
      h('div', { style: 'flex: 1; display: flex; flex-direction: column; gap: 4px;' }, [
        h('span', { style: 'font-size: 18px; font-weight: 900; color: #0f172a; letter-spacing: -0.5px;' }, `${loginUserInfo.nickname} ${t('common.san')}`),
        h('span', { style: 'font-size: 13px; color: #64748b; line-height: 1.6; font-weight: 500;' }, [t('chat.welcome_msg_1'), h('br'), t('chat.welcome_msg_2')]),
      ]),
    ]),
  })
}

export type NotifyType = 'success' | 'warning' | 'info' | 'error'
export const showAppNotification = (message: string, title: string = '', type: NotifyType = 'info') => {
  ElNotification({ title, message, type, customClass: `ez-notification ${type}`, duration: 3500, offset: 70, position: 'top-right' })
}
export const notifySuccess = (msg: string, title: string = t('common.success')) => showAppNotification(msg, title, 'success')
export const notifyWarning = (msg: string, title: string = t('common.warning')) => showAppNotification(msg, title, 'warning')
export const notifyError = (msg: string, title: string = t('common.error')) => showAppNotification(msg, title, 'error')
export const notifyInfo = (msg: string, title: string = t('common.info')) => showAppNotification(msg, title, 'info')
