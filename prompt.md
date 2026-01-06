# Execution Plan - Fix Join Screen Flicker

## Goal
Solve the screen flickering issue when clicking "Join" in the `LeftCard` component. The user requested to avoid full-screen overlays and suggested using the View Transitions API.

## Analysis
*   **Cause**: The flicker is likely caused by `appStore.runWithLoading` triggering a global full-screen spinner overlay during the validation phase, followed by an abrupt page navigation.
*   **Solution**:
    1.  **Disable Global Loading**: Remove `appStore.runWithLoading` from `useJoinInput.ts`'s `validateAndGetPayload`. Rely on the existing `isValidating` ref to show local loading state (e.g., button spinner) instead of the global overlay.
    2.  **Smooth Navigation**: In `LeftCard.vue`, wrap the `router.push` call with `document.startViewTransition` (if available) to provide a smooth visual transition between the home page and the join page.

## Proposed Changes

1.  **Modify `frontend/vue-ezchat/src/hooks/chat/join/useJoinInput.ts`**:
    *   In `validateAndGetPayload`, remove the `appStore.runWithLoading` wrapper.
    *   Manually set `isValidating.value = true` before validation and `false` in `finally`.
    *   This ensures the "Join" button shows a spinner (local feedback) without covering the entire screen.

2.  **Modify `frontend/vue-ezchat/src/views/index/components/LeftCard.vue`**:
    *   Update `handleValidateAndRedirect`:
        *   Check for `document.startViewTransition`.
        *   If available, wrap `router.push` in it.
        *   Fallback to normal `router.push` if not supported.

## Verification
*   Click "Join" in `LeftCard`.
*   Verify no full-screen loading spinner appears (only button spinner).
*   Verify navigation to `/Join/:id` happens smoothly (with View Transition if supported).
