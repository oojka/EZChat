<script setup lang="ts">
/**
 * 移动端设置页组件
 *
 * 功能：
 * - 用户设置中心（个人资料、安全设置）
 * - 访客用户升级为正式用户功能
 * - 用户注销登录
 * - 多页面布局（主菜单 → 子页面）
 *
 * 路由：/chat/settings（移动端设置页）
 *
 * 依赖：
 * - useUserStore: 用户状态管理
 * - ProfileTab: 个人资料编辑组件
 * - SecurityTab: 安全设置组件
 * - upgradeUserApi: 用户升级API
 */
import { ref, computed, onMounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/userStore'
import ProfileTab from '@/components/dialogs/user-settings/ProfileTab.vue'
import SecurityTab from '@/components/dialogs/user-settings/SecurityTab.vue'
import { ArrowRight, User as UserIcon, Lock, InfoFilled } from '@element-plus/icons-vue'
import PasswordInput from '@/components/PasswordInput.vue'
import Avatar from '@/components/Avatar.vue'
import { ElMessage, type FormInstance, type FormRules, type UploadProps } from 'element-plus'
import { upgradeUserApi } from '@/api/User'
import { uploadAvatarApi } from '@/api/Auth'
import type { RegisterInfo, Image } from '@/type'
import { isValidUsername, isValidNickname, isValidPassword } from '@/utils/validators'
import { showConfirmDialog } from '@/components/dialogs/confirmDialog.ts'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isGuest = computed(() => userStore.loginUserInfo?.userType === 'guest')

type SettingsSection = 'main' | 'profile' | 'security' | 'upgrade'
const currentSection = ref<SettingsSection>('main')

const emptyAvatar: Image = {
  imageUrl: '',
  imageThumbUrl: ''
}

/**
 * 组件挂载时的初始化逻辑
 *
 * 功能：
 * - 检查URL查询参数中是否有升级标志
 * - 如果有升级标志，初始化升级表单并跳转到升级页面
 * - 清除查询参数以避免URL泄露升级状态
 */
onMounted(() => {
  if (route.query.upgrade === 'true') {
    initUpgradeForm()
    currentSection.value = 'upgrade'
    router.replace({ path: '/chat/settings', query: {} })
  }
})

/**
 * 导航到指定的设置子页面
 *
 * 功能：
 * - 切换当前显示的设置页面
 * - 如果目标是升级页面，先初始化升级表单
 * - 支持页面间导航状态管理
 *
 * @param section - 目标页面标识
 *   - 'main': 主菜单页面
 *   - 'profile': 个人资料编辑页面
 *   - 'security': 安全设置页面
 *   - 'upgrade': 访客升级页面
 */
const goToSection = (section: SettingsSection) => {
  if (section === 'upgrade') {
    initUpgradeForm()
  }
  currentSection.value = section
}

/**
 * 返回设置主菜单页面
 *
 * 功能：
 * - 从任何子页面返回到主菜单
 * - 重置当前页面状态到'main'
 */
const goBack = () => {
  currentSection.value = 'main'
}

/**
 * 处理用户注销流程
 *
 * 步骤：
 * 1. 显示确认对话框，防止误操作
 * 2. 用户确认后调用用户存储的注销方法
 * 3. 跳转到应用首页
 * 4. 传递参数避免重复显示注销确认
 */
const handleLogout = async () => {
  showConfirmDialog({
    title: 'common.confirm',
    message: 'auth.logout_confirm',
    confirmText: 'auth.logout',
    type: 'danger',
    onConfirm: async () => {
      await userStore.logout({ showDialog: false })
      await router.push('/')
    }
  })
}

const formRef = ref<FormInstance>()
const form = reactive<RegisterInfo>({
  nickname: '',
  bio: '',
  username: '',
  password: '',
  confirmPassword: '',
  avatar: { ...emptyAvatar }
})
const isSubmitting = ref(false)

/**
 * 初始化访客升级表单
 *
 * 功能：
 * - 清空用户名、密码和确认密码字段（需要用户重新输入）
 * - 预填充当前用户的昵称和个人简介
 * - 复制当前用户的头像信息（如果存在）
 * - 为升级流程准备初始数据
 */
const initUpgradeForm = () => {
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
  form.nickname = userStore.loginUserInfo?.nickname || ''
  form.bio = userStore.loginUserInfo?.bio || ''
  form.avatar = userStore.loginUserInfo?.avatar ? { ...userStore.loginUserInfo.avatar } : { ...emptyAvatar }
}

/**
 * 确认密码验证器
 *
 * 功能：
 * - 验证确认密码字段是否已填写
 * - 验证确认密码是否与密码字段一致
 * - 符合Element Plus表单验证器的回调格式
 *
 * @param _rule - 验证规则对象（未使用）
 * @param value - 输入的确认密码值
 * @param callback - 验证回调函数
 */
const validatePass2 = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value === '') {
    callback(new Error(t('validation.confirm_password_required')))
  } else if (value !== form.password) {
    callback(new Error(t('validation.password_mismatch')))
  } else {
    callback()
  }
}

/**
 * 升级表单验证规则定义
 *
 * 字段说明：
 * - nickname: 昵称（必填，格式验证）
 * - username: 用户名（必填，格式验证）
 * - password: 密码（必填，格式验证）
 * - confirmPassword: 确认密码（必填，一致性验证）
 */
const formRules = reactive<FormRules>({
  nickname: [
    { required: true, message: t('validation.nickname_required'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isValidNickname(value as string)) callback(new Error(t('validation.nickname_format')))
        else callback()
      }, trigger: 'blur'
    }
  ],
  username: [
    { required: true, message: t('validation.username_required'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isValidUsername(value as string)) callback(new Error(t('validation.username_format')))
        else callback()
      }, trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: t('validation.password_required'), trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (!isValidPassword(value as string)) callback(new Error(t('validation.password_format')))
        else callback()
      }, trigger: 'blur'
    }
  ],
  confirmPassword: [
    { validator: validatePass2, trigger: 'blur' }
  ]
})

/**
 * 自定义头像上传请求处理
 *
 * 功能：
 * - 替代Element Plus默认的上传实现
 * - 调用后端头像上传API
 * - 处理上传成功和失败的回调
 *
 * @param options - 上传选项
 * @param options.file - 上传的文件对象
 * @param options.onSuccess - 上传成功回调
 * @param options.onError - 上传失败回调
 */
const customUploadRequest = async (options: { file: File; onSuccess: (res: unknown) => void; onError: (err: unknown) => void }) => {
  const { file, onSuccess, onError } = options
  try {
    const res = await uploadAvatarApi(file)
    onSuccess(res)
  } catch (err) {
    onError(err)
  }
}

/**
 * 头像上传成功处理函数
 *
 * 功能：
 * - 更新表单中的头像数据
 * - 显示上传成功提示
 * - 处理API响应数据结构
 *
 * @param response - 上传API的响应对象
 */
const handleAvatarSuccess: UploadProps['onSuccess'] = (response) => {
  if (response?.data) {
    form.avatar = response.data
    ElMessage.success(t('upgrade.avatar_upload_success'))
    return
  }
  ElMessage.error(t('upgrade.avatar_upload_failed'))
}

/**
 * 头像上传前验证函数
 *
 * 验证规则：
 * 1. 文件格式：仅支持JPEG和PNG格式
 * 2. 文件大小：不超过2MB
 *
 * @param rawFile - 原始文件对象
 * @returns 是否允许上传
 */
const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  if (rawFile.type !== 'image/jpeg' && rawFile.type !== 'image/png') {
    ElMessage.error(t('upgrade.avatar_format_error'))
    return false
  } else if (rawFile.size / 1024 / 1024 > 2) {
    ElMessage.error(t('upgrade.avatar_size_error'))
    return false
  }
  return true
}

/**
 * 处理访客升级表单提交
 *
 * 步骤：
 * 1. 验证表单数据有效性
 * 2. 设置提交加载状态
 * 3. 调用升级API
 * 4. 处理API响应：
 *    - 成功：更新用户状态，返回主菜单，显示成功提示
 *    - 失败：显示错误消息
 * 5. 无论成功失败，最终清除加载状态
 */
const handleUpgradeSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  isSubmitting.value = true
  try {
    const res = await upgradeUserApi(form)
    if (res.status === 1 && res.data) {
      ElMessage.success(t('upgrade.success'))
      userStore.setLoginUser(res.data)
      goBack()
    } else {
      ElMessage.error(res.message || t('upgrade.failed'))
    }
  } catch {
    ElMessage.error(t('upgrade.failed'))
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="mobile-settings-view">
    <Transition name="slide-fade" mode="out-in">
      <div v-if="currentSection === 'main'" key="main" class="settings-main">
        <div class="mobile-page-header">
          <h1 class="page-title">{{ t('mobile.tab_settings') }}</h1>
        </div>

        <div class="settings-list">
          <div v-if="isGuest" class="upgrade-banner" @click="goToSection('upgrade')">
            <el-icon class="banner-icon">
              <InfoFilled />
            </el-icon>
            <div class="banner-text">
              <span class="banner-title">{{ t('mobile.upgrade_banner_title') }}</span>
              <span class="banner-desc">{{ t('mobile.upgrade_banner_desc') }}</span>
            </div>
            <el-icon class="banner-arrow">
              <ArrowRight />
            </el-icon>
          </div>

          <div class="settings-section">
            <div class="settings-item" @click="goToSection('profile')">
              <el-icon class="item-icon">
                <UserIcon />
              </el-icon>
              <span class="item-label">{{ t('user_settings.profile_tab') }}</span>
              <el-icon class="item-arrow">
                <ArrowRight />
              </el-icon>
            </div>

            <div v-if="!isGuest" class="settings-item" @click="goToSection('security')">
              <el-icon class="item-icon">
                <Lock />
              </el-icon>
              <span class="item-label">{{ t('user_settings.security_tab') }}</span>
              <el-icon class="item-arrow">
                <ArrowRight />
              </el-icon>
            </div>
          </div>

          <div class="settings-section">
            <div class="settings-item logout-item" @click="handleLogout">
              <span class="item-label">{{ t('auth.logout') }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-else-if="currentSection === 'profile'" key="profile" class="settings-subpage">
        <div class="subpage-header">
          <el-button text @click="goBack">{{ t('common.back') }}</el-button>
          <h2 class="subpage-title">{{ t('user_settings.profile_tab') }}</h2>
          <div class="header-spacer"></div>
        </div>
        <div class="subpage-content">
          <ProfileTab @close="goBack" />
        </div>
      </div>

      <div v-else-if="currentSection === 'security'" key="security" class="settings-subpage">
        <div class="subpage-header">
          <el-button text @click="goBack">{{ t('common.back') }}</el-button>
          <h2 class="subpage-title">{{ t('user_settings.security_tab') }}</h2>
          <div class="header-spacer"></div>
        </div>
        <div class="subpage-content">
          <SecurityTab @close="goBack" />
        </div>
      </div>

      <div v-else-if="currentSection === 'upgrade'" key="upgrade" class="settings-subpage">
        <div class="subpage-header">
          <el-button text @click="goBack">{{ t('common.back') }}</el-button>
          <h2 class="subpage-title">{{ t('upgrade.dialog_title') }}</h2>
          <div class="header-spacer"></div>
        </div>
        <div class="subpage-content upgrade-content">
          <div class="upgrade-avatar-section">
            <el-upload class="avatar-uploader" :show-file-list="false" :http-request="customUploadRequest"
              :on-success="handleAvatarSuccess" :before-upload="beforeAvatarUpload">
              <Avatar v-if="form.avatar.imageUrl" :image="form.avatar" :size="80" class="upgrade-avatar" />
              <div v-else class="avatar-placeholder">
                <el-icon :size="32">
                  <UserIcon />
                </el-icon>
              </div>
            </el-upload>
            <span class="avatar-tip">{{ t('upgrade.avatar_tip') }}</span>
          </div>

          <el-form ref="formRef" :model="form" :rules="formRules" label-position="top" class="upgrade-form">
            <el-form-item prop="nickname">
              <el-input v-model="form.nickname" :placeholder="t('upgrade.nickname_placeholder')" size="large" />
            </el-form-item>

            <el-form-item prop="bio">
              <el-input v-model="form.bio" :placeholder="t('upgrade.bio_placeholder')" type="textarea" :rows="2" />
            </el-form-item>

            <el-form-item prop="username">
              <el-input v-model="form.username" :placeholder="t('upgrade.username_placeholder')" size="large" />
            </el-form-item>

            <el-form-item prop="password">
              <PasswordInput v-model="form.password" :placeholder="t('upgrade.password_placeholder')" size="large" />
            </el-form-item>

            <el-form-item prop="confirmPassword">
              <PasswordInput v-model="form.confirmPassword" :placeholder="t('upgrade.confirm_password_placeholder')"
                size="large" />
            </el-form-item>

            <el-button type="primary" size="large" :loading="isSubmitting" class="upgrade-submit-btn"
              @click="handleUpgradeSubmit">
              {{ t('upgrade.submit_button') }}
            </el-button>
          </el-form>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.mobile-settings-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-page);
  /* padding-bottom removed, layout handled by flex */
  /* padding-bottom: calc(var(--tabbar-height) + var(--safe-area-bottom)); */
  box-sizing: border-box;
}

.settings-main {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.mobile-page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--el-border-color-light);
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-900);
  margin: 0;
}

.settings-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

.upgrade-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-dark) 100%);
  border-radius: var(--radius-base);
  margin-bottom: 16px;
  cursor: pointer;
  color: #fff;
}

.banner-icon {
  font-size: 24px;
  flex-shrink: 0;
}

.banner-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.banner-title {
  font-size: 14px;
  font-weight: 600;
}

.banner-desc {
  font-size: 12px;
  opacity: 0.9;
}

.banner-arrow {
  font-size: 16px;
  opacity: 0.8;
}

.settings-section {
  background: var(--bg-card);
  border-radius: var(--radius-base);
  overflow: hidden;
  margin-bottom: 16px;
}

.settings-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  transition: background 0.2s ease;
  border-bottom: 1px solid var(--el-border-color-light);
}

.settings-item:last-child {
  border-bottom: none;
}

.settings-item:active {
  background: var(--bg-page);
}

.item-icon {
  font-size: 20px;
  color: var(--text-500);
}

.item-label {
  flex: 1;
  font-size: 15px;
  color: var(--text-900);
}

.item-arrow {
  font-size: 14px;
  color: var(--text-400);
}

.logout-item {
  justify-content: center;
}

.logout-item .item-label {
  flex: none;
  color: #f56c6c;
  font-weight: 500;
}

.settings-subpage {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
}

.subpage-header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.subpage-title {
  flex: 1;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-900);
  margin: 0;
}

.header-spacer {
  width: 60px;
}

.subpage-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  min-height: 0;
}

.upgrade-content {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.upgrade-avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-bottom: 24px;
}

.avatar-uploader {
  cursor: pointer;
}

.upgrade-avatar {
  border-radius: 50%;
}

.avatar-placeholder {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: var(--bg-page);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-400);
  border: 2px dashed var(--el-border-color-light);
}

.avatar-tip {
  font-size: 12px;
  color: var(--text-500);
}

.upgrade-form {
  width: 100%;
  max-width: 400px;
}

.upgrade-submit-btn {
  width: 100%;
  margin-top: 16px;
}

.slide-fade-enter-active,
.slide-fade-leave-active {
  transition: all 0.2s ease;
}

.slide-fade-enter-from {
  opacity: 0;
  transform: translateX(20px);
}

.slide-fade-leave-to {
  opacity: 0;
  transform: translateX(-20px);
}
</style>
