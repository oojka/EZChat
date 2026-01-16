import {h} from 'vue'
import {ElNotification} from 'element-plus'
import type {Image, LoginUserInfo} from '@/type'
import router from '@/router'
import i18n from '@/i18n'
import { resolveImageUrl } from '@/utils/imageUrl'

const { t } = i18n.global

export type MessageNotificationSender = {
  nickname?: string
}

export type MessageNotificationOptions = {
  chatCode: string
  chatName: string
  chatCover?: Image | null
  previewText?: string
  sender?: MessageNotificationSender | null
  title?: string
}

export const showMessageNotification = (options: MessageNotificationOptions) => {
  const { chatCode, chatName, chatCover, previewText, sender, title: customTitle } = options
  
  const senderName = sender?.nickname?.trim() || ''
  const title = customTitle?.trim() || chatName.trim() || t('chat.new_message')
  const body = senderName ? `${senderName}: ${previewText || t('chat.new_message')}` : (previewText || t('chat.new_message'))
  const trimmedChatName = chatName.trim()
  const firstChar = trimmedChatName.charAt(0)?.toUpperCase() || '?'
  const safeFirstChar = firstChar.replace(/[<>&"']/g, '')
  
  const { primary: primaryUrl, fallback: fallbackUrl } = resolveImageUrl(chatCover, { preferThumb: true })
  const initialUrl = primaryUrl || fallbackUrl || ''

  const handleAvatarError = (event: Event) => {
    const imgElement = event.target as HTMLImageElement
    if (!imgElement) return

    const currentSrc = imgElement.getAttribute('src') || ''

    if (currentSrc === primaryUrl && fallbackUrl && fallbackUrl !== primaryUrl) {
      imgElement.src = fallbackUrl
      return
    }

    const container = imgElement.closest('.ez-notify-avatar-wrapper')
    if (container) {
      container.innerHTML = `<div class="ez-notify-avatar ez-notify-avatar-fallback">${safeFirstChar}</div>`
    }
  }

  ElNotification({
    title,
    customClass: 'ez-notification info',
    offset: 70,
    position: 'top-right',
    duration: 4000,
    message: h('div', { class: 'ez-notify-content' }, [
      initialUrl
        ? h('div', { class: 'ez-notify-avatar-wrapper' }, [
            h('img', {
              src: initialUrl,
              class: 'ez-notify-avatar',
              onError: handleAvatarError
            })
          ])
        : h('div', { class: 'ez-notify-avatar-wrapper' }, [
            h('div', { class: 'ez-notify-avatar ez-notify-avatar-fallback' }, safeFirstChar)
          ]),
      h('div', { class: 'ez-notify-info' }, [
        h('span', { class: 'ez-notify-preview' }, body),
      ]),
    ]),
    onClick: () => { router.push(`/chat/${chatCode}`).catch(() => {}) },
  })
}

export const showWelcomeNotification = (loginUserInfo: LoginUserInfo) => {
  const { primary: primaryUrl, fallback: fallbackUrl } = resolveImageUrl(loginUserInfo.avatar, { preferThumb: true })
  const initialUrl = primaryUrl || fallbackUrl || ''
  const nickname = loginUserInfo.nickname || ''
  const firstChar = nickname.charAt(0)?.toUpperCase() || '?'

  const handleAvatarError = (event: Event) => {
    const imgElement = event.target as HTMLImageElement
    if (!imgElement) return

    const currentSrc = imgElement.getAttribute('src') || ''

    if (currentSrc === primaryUrl && fallbackUrl && fallbackUrl !== primaryUrl) {
      imgElement.src = fallbackUrl
      return
    }

    const container = imgElement.closest('.ez-welcome-avatar-wrapper')
    if (container) {
      container.innerHTML = `<div class="ez-welcome-avatar ez-welcome-avatar-fallback">${firstChar}</div>`
    }
  }

  ElNotification({
    title: t('auth.welcome_back'),
    customClass: 'ez-notification welcome',
    offset: 70,
    position: 'top-right',
    duration: 8000,
    message: h('div', { class: 'ez-welcome-content' }, [
      initialUrl
        ? h('div', { class: 'ez-welcome-avatar-wrapper' }, [
            h('img', {
              src: initialUrl,
              class: 'ez-welcome-avatar',
              onError: handleAvatarError
            })
          ])
        : h('div', { class: 'ez-welcome-avatar-wrapper' }, [
            h('div', { class: 'ez-welcome-avatar ez-welcome-avatar-fallback' }, firstChar)
          ]),
      h('div', { class: 'ez-welcome-info' }, [
        h('span', { class: 'ez-welcome-nickname' }, `${nickname} ${t('common.san')}`),
        h('span', { class: 'ez-welcome-message' }, [t('chat.welcome_msg_1'), h('br'), t('chat.welcome_msg_2')]),
      ]),
    ]),
  })
}

export type NotifyType = 'success' | 'warning' | 'info' | 'error'
/**
 * 统一的应用级通知封装
 *
 * @param message 内容
 * @param title 标题（可选）
 * @param type 通知类型（影响样式）
 */
export const showAppNotification = (message: string, title: string = '', type: NotifyType = 'info') => {
  ElNotification({ title, message, type, customClass: `ez-notification ${type}`, duration: 3500, offset: 70, position: 'top-right' })
}

/** 成功提示 */
export const notifySuccess = (msg: string, title: string = t('common.success')) => showAppNotification(msg, title, 'success')
/** 警告提示 */
export const notifyWarning = (msg: string, title: string = t('common.warning')) => showAppNotification(msg, title, 'warning')
/** 错误提示 */
export const notifyError = (msg: string, title: string = t('common.error')) => showAppNotification(msg, title, 'error')
/** 信息提示 */
export const notifyInfo = (msg: string, title: string = t('common.info')) => showAppNotification(msg, title, 'info')
