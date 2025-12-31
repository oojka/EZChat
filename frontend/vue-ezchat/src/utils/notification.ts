import {h} from 'vue'
import {ElNotification} from 'element-plus'
import type {LoginUserInfo, Message, User} from '@/type'
import router from '@/router'
import i18n from '@/i18n' // 引入 i18n 实例

const { t } = i18n.global

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
    message: h('div', { class: 'ez-notify-content' }, [
      h('img', { src: sender.avatar?.objectThumbUrl || '', class: 'ez-notify-avatar' }),
      h('div', { class: 'ez-notify-info' }, [
        h('span', { class: 'ez-notify-sender' }, sender.nickname),
        h('span', { class: 'ez-notify-preview' }, previewText),
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
    message: h('div', { class: 'ez-welcome-content' }, [
      h('img', {
        src: loginUserInfo.avatar?.objectThumbUrl || loginUserInfo.avatar?.objectUrl || '',
        class: 'ez-welcome-avatar'
      }),
      h('div', { class: 'ez-welcome-info' }, [
        h('span', { class: 'ez-welcome-nickname' }, `${loginUserInfo.nickname} ${t('common.san')}`),
        h('span', { class: 'ez-welcome-message' }, [t('chat.welcome_msg_1'), h('br'), t('chat.welcome_msg_2')]),
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
