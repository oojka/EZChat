# 移动端首页改造 - 工作计划草稿

**目标**: 深度改造 EZChat 移动端首页，支持访客功能、登录功能、注册功能的移动优化布局和样式

**目标设备**: iPhone 16 (393×852 逻辑像素)

---

## 当前状态分析

### 桌面端实现 (src/views/index/index.vue)

**设计模式**: 3D 翻转卡片
- **LeftCard**: 访客加入（正面=功能介绍 / 背面=加入表单）
- **RightCard**: 登录+注册（正面=登录表单 / 背面=注册向导）

**卡片尺寸**: 460px × 420px（桌面）

**交互模式**:
1. 点击卡片激活并居中放大
2. 卡片内部点击"翻转"按钮切换正反面
3. 非激活卡片模糊+半透明

**表单内容**:
- **访客**: Room ID/密码 或 邀请链接（Tab 切换）
- **登录**: 用户名 + 密码
- **注册**: 3步向导（头像 → 昵称+用户名 → 密码+确认密码）

**移动端问题**:
- 翻转卡片在小屏幕上体验差
- 垂直堆叠后需要大量滚动
- 虚拟键盘弹出时内容被遮挡
- 表单输入框和按钮触摸目标不够大
- 注册向导的步进在移动端空间不足

---

## 架构决策

### 方案对比

| 方案 | 优点 | 缺点 | 推荐指数 |
|------|------|------|----------|
| **A. 单页 Tab 切换** | 代码简洁，无路由切换开销 | 3个功能挤在一页可能拥挤 | ⭐⭐⭐ |
| **B. 分离多页** | 每个功能独立空间充足 | 路由复杂，首屏需要引导 | ⭐⭐ |
| **C. 混合模式** | 登录+注册合并，访客独立 | 架构不一致，维护成本高 | ⭐⭐⭐⭐ |

### ✅ 推荐方案：**C. 混合模式 + Tab 切换**

**理由**:
1. **登录/注册** 本就是同一流程的两个入口，合并符合用户心智
2. **访客模式** 是快速通道，独立展示更突出
3. **Tab 切换** 在 393px 宽度下可以清晰展示
4. **符合行业惯例**: 大部分移动应用采用此模式

**架构设计**:
```
移动端首页 (/m 或根据 isMobile 判断)
├─ 顶部区域
│  ├─ Logo + 标题
│  ├─ 语言切换
│  └─ 主题切换
├─ 主内容区（Segmented Control Tab）
│  ├─ Tab 1: 访客加入
│  ├─ Tab 2: 登录
│  └─ Tab 3: 注册
└─ 底部安全区
```

---

## 详细设计规格

### 1. 布局结构

**页面容器**:
```css
.mobile-index-view {
  height: var(--app-height); /* iOS Safari 动态视口 */
  display: flex;
  flex-direction: column;
  padding: var(--safe-area-top) 0 var(--safe-area-bottom);
  background: linear-gradient(...); /* 保留品牌渐变 */
}
```

**内容分区**:
- **Header**: 固定高度 80px（Logo 60px + 上下间距）
- **Tab Bar**: 固定高度 48px
- **Content Area**: flex: 1（可滚动）
- **Actions**: 固定底部 60px（按钮区）

### 2. Tab 切换实现

**UI 组件**: Element Plus `el-segmented`（如无，用 `el-radio-group` + 自定义样式）

**Tab 状态**:
```typescript
const activeTab = ref<'guest' | 'login' | 'register'>('login')
```

**切换动画**: `el-fade-in-linear` 淡入淡出

### 3. 各 Tab 内容设计

#### Tab 1: 访客加入

**布局**:
```
[ Icon 装饰 ]
「快速加入」标题
[ Room ID / 邀请链接切换 ]
[ 输入区域 ]
[ 可选密码输入 ]
[ 加入按钮 ]
```

**关键样式**:
- 输入框 min-height: 48px（触摸友好）
- 按钮 height: 52px, border-radius: 12px
- 间距: gap 16px

#### Tab 2: 登录

**布局**:
```
「登录」标题
[ 用户名输入 ]
[ 密码输入 ]
[ 登录按钮 ]
[ 没有账号？注册 ] ← 点击切换到 Tab 3
```

**交互优化**:
- 回车键提交
- 锁定态显示倒计时
- Loading 状态禁用输入

#### Tab 3: 注册

**挑战**: 3步向导在移动端空间不足

**解决方案**: 简化为 **2步**
- **步骤 1**: 头像（可选）+ 昵称 + 用户名
- **步骤 2**: 密码 + 确认密码

**进度指示**: 顶部小型进度条（2个点）

**向导控制**:
```
步骤1: [下一步] (全宽按钮)
步骤2: [返回] [注册] (各占50%)
```

### 4. 响应式键盘处理

**问题**: 虚拟键盘弹出时遮挡内容

**方案**:
```typescript
import { useKeyboardVisible } from '@/composables/useKeyboardVisible'

const { isKeyboardVisible } = useKeyboardVisible()
```

**CSS 调整**:
```css
.content-area {
  /* 键盘弹出时隐藏装饰元素，增加可用空间 */
  .decorative-blob {
    transition: opacity 0.2s;
  }
}

.is-keyboard-visible .decorative-blob {
  opacity: 0;
}
```

### 5. 暗黑模式适配

**原则**: 复用现有 CSS 变量
- `--bg-page`, `--bg-card`, `--bg-glass`
- `--text-900`, `--text-500`
- `--primary`, `--border-glass`

**玻璃态效果**: 移动端保留毛玻璃（性能影响小）

---

## 实现方案

### 方案 A: 独立移动端页面（推荐）

**新建文件**: `src/views/mobile/IndexView.vue`

**路由配置**:
```typescript
// router/index.ts
{
  path: '/',
  component: () => {
    const { isMobile } = useIsMobile()
    return isMobile.value 
      ? import('@/views/mobile/IndexView.vue')
      : import('@/views/index/index.vue')
  }
}
```

**优点**:
- 桌面/移动完全隔离，互不影响
- 可以大胆删除移动端不需要的代码
- 未来维护清晰

### 方案 B: 条件渲染（备选）

**修改现有**: `src/views/index/index.vue`

```vue
<template>
  <MobileIndexLayout v-if="isMobile" />
  <DesktopIndexLayout v-else />
</template>
```

**缺点**:
- 单文件代码量翻倍
- 桌面端改动可能误伤移动端

---

## 组件复用策略

### 可复用的业务逻辑 Composables

✅ **useLogin** - 登录逻辑（完全复用）
✅ **useRegister** - 注册逻辑（复用，但简化步骤）
✅ **useJoinInput** - 访客加入逻辑（完全复用）

### 需要新建的 UI 组件

❌ **不复用** LeftCard/RightCard（翻转卡片不适合移动端）

✅ **新建**:
- `MobileGuestForm.vue` - 访客加入表单
- `MobileLoginForm.vue` - 登录表单
- `MobileRegisterForm.vue` - 简化注册向导

### 共享组件

✅ **直接复用**:
- `PasswordInput.vue` - 密码输入组件
- `AppLogo.vue` - Logo 组件

---

## 技术清单

### 必需工具/Composables

| Composable | 用途 | 路径 |
|------------|------|------|
| `useIsMobile()` | 设备判断 | `@/composables/useIsMobile.ts` |
| `useViewportHeight()` | iOS 动态视口 | `@/composables/useViewportHeight.ts` |
| `useKeyboardVisible()` | 键盘检测 | `@/composables/useKeyboardVisible.ts` |

### Element Plus 组件

- `el-input` (size="large")
- `el-button` (type="primary", size="large")
- `el-radio-group` + `el-radio-button`（模拟 Segmented Control）
- `el-form` + `el-form-item`
- `el-upload`（注册头像）
- `el-progress`（注册进度）

### 动画/过渡

- `el-fade-in-linear` - Tab 切换
- CSS `transition` - 输入框焦点/按钮 hover

---

## 国际化 (i18n) 需求

### 新增 key

```json
{
  "mobile": {
    "index": {
      "tab_guest": "访客",
      "tab_login": "登录",
      "tab_register": "注册",
      "quick_join": "快速加入",
      "join_room_hint": "输入房间 ID 或邀请链接"
    }
  }
}
```

**需要覆盖**: zh, en, ja, ko, zh-tw（5种语言）

---

## 样式规范

### 触摸目标尺寸

- **最小高度**: 44px（iOS 标准）
- **推荐高度**: 48-52px（更舒适）
- **间距**: 12-16px（防止误触）

### 字体尺寸

- **标题**: 24px (font-weight: 800)
- **输入框文字**: 16px（防止 iOS 自动缩放）
- **按钮文字**: 16px (font-weight: 700)
- **提示文字**: 13px (color: var(--text-500))

### 圆角

- **卡片/容器**: 16px
- **按钮**: 12px
- **输入框**: 10px

---

## 测试点

### 功能测试

- [ ] 访客加入（Room ID 模式）
- [ ] 访客加入（邀请链接模式）
- [ ] 登录成功/失败/锁定
- [ ] 注册 2步流程完整走通
- [ ] 头像上传（可选）
- [ ] 默认头像自动应用
- [ ] Tab 切换动画流畅
- [ ] 表单验证正确触发

### 响应式测试

- [ ] 虚拟键盘弹出不遮挡输入框
- [ ] 竖屏 393×852 正常显示
- [ ] 横屏自动调整布局（可选）
- [ ] iOS Safari 地址栏隐藏后高度正确

### 交互测试

- [ ] 触摸目标足够大
- [ ] 无误触风险
- [ ] Loading 状态正确反馈
- [ ] 错误提示清晰可见

---

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 注册简化导致体验下降 | 中 | 保持头像上传可选，默认头像自动生成 |
| Tab 切换状态丢失 | 低 | 使用 `keep-alive` 缓存未激活 Tab |
| 键盘遮挡严重 | 高 | 使用 `useKeyboardVisible` + 动态调整布局 |
| 桌面端意外受影响 | 低 | 独立文件方案（方案A）完全隔离 |

---

## 下一步行动

1. ✅ 确认架构方案（混合模式 + Tab 切换）
2. ⏳ 创建 `MobileIndexView.vue` 主文件
3. ⏳ 拆分3个子表单组件
4. ⏳ 集成业务逻辑 Composables
5. ⏳ 添加 i18n 翻译（5种语言）
6. ⏳ 路由配置（移动端判断）
7. ⏳ 样式调优（安全区、键盘适配）
8. ⏳ 功能测试 + 真机验证

---

**创建时间**: 2026-01-11
**状态**: 草稿 - 等待用户确认
