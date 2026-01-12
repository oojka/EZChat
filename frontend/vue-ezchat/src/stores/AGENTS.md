# Pinia Stores

**Purpose**: Global state management with reactive persistence.

## STRUCTURE

```
stores/
├── appStore.ts       # App-level state (favicon, init, locale)
├── userStore.ts      # Current user, auth state
├── chatStore.ts      # Active chat, chat list
├── messageStore.ts   # Messages, send queue, pagination
├── memberStore.ts    # Room members, presence
├── friendStore.ts    # Friends list, requests
├── sidebarStore.ts   # UI sidebar state
├── notificationStore.ts  # Toast/notifications
└── formStore.ts      # Form drafts
```

## WHERE TO LOOK

| Task | File |
|------|------|
| User auth state | `userStore.ts` |
| Chat selection | `chatStore.ts` |
| Message ops | `messageStore.ts` |
| Friend system | `friendStore.ts` |
| App init | `appStore.ts` |

## CONVENTIONS

**Definition**:
```typescript
export const useXxxStore = defineStore('xxx', () => {
  const state = ref(...)
  const action = async () => {...}
  return { state, action }
})
```

**Usage in components**:
```typescript
const store = useXxxStore()
const { prop1, prop2 } = storeToRefs(store)  // Reactive
store.action()  // Methods directly
```

**Persistence**: Use `localStorage` for:
- `refreshToken` (userStore)
- `locale` (appStore)
- `chatCode` of last active chat

## ANTI-PATTERNS

- Destructure without `storeToRefs` → Loses reactivity
- Store state in components → Use stores
- Direct localStorage in components → Use store persistence
