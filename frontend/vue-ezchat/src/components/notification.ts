import {h} from 'vue'
import {ElNotification} from 'element-plus'
import type {Image, LoginUserInfo, Message} from '@/type'
import router from '@/router'
import i18n from '@/i18n' // 引入 i18n 实例

const { t } = i18n.global

/**
 * 通知中展示发送内容的“预览文本”
 *
 * 业务目的：
 * - 文本消息：展示文本
 * - 图片消息：使用 `[画像]` 标签占位，避免通知内容为空
 */
const getPreviewContent = (message: Message): string => {
  let content = message.text || ''
  if (message.images && message.images.length > 0) {
    content += `[${t('chat.image')}]`.repeat(message.images.length)
  }
  return content || `[${t('chat.new_message')}]`
}

/**
 * 通知发送者的最小结构
 *
 * 说明：通知只需要昵称与头像，不关心在线状态字段（避免 ChatMember/User 类型不兼容）。
 */
type NotificationSender = {
  nickname: string
  avatar: Image
}

/**
 * 显示“新消息”通知
 *
 * 业务场景：当收到非当前房间的新消息时，弹出右上角通知并支持点击跳转到对应房间。
 *
 * @param message 消息对象
 * @param sender 发送者信息（最小字段：nickname/avatar）
 * @param chatName 房间名称（用于通知标题）
 */
export const showMessageNotification = (message: Message, sender: NotificationSender, chatName: string) => {
  const previewText = getPreviewContent(message)
  const thumbUrl = sender.avatar?.objectThumbUrl || ''
  const originalUrl = sender.avatar?.objectUrl || ''
  const initialUrl = thumbUrl || originalUrl || ''
  const nickname = sender.nickname || ''
  const firstChar = nickname.charAt(0)?.toUpperCase() || '?'
  
  // 头像加载失败处理：缩略图失败时尝试原图，原图失败则显示默认占位
  const handleAvatarError = (event: Event) => {
    const imgElement = event.target as HTMLImageElement
    if (!imgElement) return
    
    const currentSrc = imgElement.src
    
    // 如果当前是缩略图且存在原图，切换到原图
    if (currentSrc === thumbUrl && originalUrl && originalUrl !== thumbUrl) {
      imgElement.src = originalUrl
      return
    }
    
    // 原图也失败或没有原图：替换为文字占位符
    const container = imgElement.closest('.ez-notify-avatar-wrapper')
    if (container) {
      container.innerHTML = `<div class="ez-notify-avatar ez-notify-avatar-fallback">${firstChar}</div>`
    }
  }

  ElNotification({
    title: chatName || t('chat.new_message'),
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
            h('div', { class: 'ez-notify-avatar ez-notify-avatar-fallback' }, firstChar)
          ]),
      h('div', { class: 'ez-notify-info' }, [
        h('span', { class: 'ez-notify-sender' }, sender.nickname),
        h('span', { class: 'ez-notify-preview' }, previewText),
      ]),
    ]),
    // 点击通知直接跳转房间
    onClick: () => { router.push(`/chat/${message.chatCode}`).catch(() => {}) },
  })
}

/**
 * 显示“欢迎回来”通知
 *
 * @param loginUserInfo 当前登录用户信息（昵称/头像）
 */
export const showWelcomeNotification = (loginUserInfo: LoginUserInfo) => {
  const thumbUrl = loginUserInfo.avatar?.objectThumbUrl || ''
  const originalUrl = loginUserInfo.avatar?.objectUrl || ''
  const initialUrl = thumbUrl || originalUrl || ''
  const nickname = loginUserInfo.nickname || ''
  const firstChar = nickname.charAt(0)?.toUpperCase() || '?'
  
  // 头像加载失败处理：缩略图失败时尝试原图，原图失败则显示默认占位
  const handleAvatarError = (event: Event) => {
    const imgElement = event.target as HTMLImageElement
    if (!imgElement) return
    
    const currentSrc = imgElement.src
    
    // 如果当前是缩略图且存在原图，切换到原图
    if (currentSrc === thumbUrl && originalUrl && originalUrl !== thumbUrl) {
      imgElement.src = originalUrl
      return
    }
    
    // 原图也失败或没有原图：替换为文字占位符
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
