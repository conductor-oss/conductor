/**
 * Returns the number of pending tasks to display as a badge on sidebar items.
 *
 * In OSS builds this always returns 0. Enterprise plugins supply their own
 * badge counts via the `badgeCount` field on SidebarItemRegistration.
 */
export const usePendingTasksCount = (): number => 0;
