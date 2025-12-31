import {createI18n} from 'vue-i18n'
import en from './locales/en.json'
import ja from './locales/ja.json'
import zh from './locales/zh.json'
import ko from './locales/ko.json'
import zhTw from './locales/zh-tw.json'

const savedLocale = localStorage.getItem('locale') || navigator.language.split('-')[0] || 'ja'

const i18n = createI18n({
  legacy: false,
  locale: savedLocale,
  fallbackLocale: 'en',
  messages: {
    en,
    ja,
    zh,
    ko,
    'zh-tw': zhTw
  }
})

export default i18n
