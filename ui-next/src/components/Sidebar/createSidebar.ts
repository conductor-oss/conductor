/**
 * Simple sidebar model: items are ordered by id/label; extensions merge via before/after.
 *
 * - SidebarItem: minimal tree node (id, label, optional children).
 * - SidebarMenuExtension: item to insert with optional before/after anchor.
 * - createSidebar(base, extensions): returns merged tree with extensions inserted.
 */

import type { SidebarItemRegistration } from "plugins/registry/types";
import type { SidebarMenuTarget } from "plugins/registry/types";

export type SidebarItem = {
  id: string;
  label: string;
  children?: SidebarItem[];
};

export type SidebarMenuExtension = {
  id: string;
  label: string;
  before?: string;
  after?: string;
  children?: SidebarMenuExtension[];
};

/** Base OSS sidebar tree (id + label only) used for ordering. Matches core menu structure. */
export const baseSidebar: SidebarItem[] = [
  {
    id: "executionsSubMenu",
    label: "Executions",
    children: [
      { id: "workflowExeItem", label: "Workflow" },
      { id: "queueMonitorItem", label: "Queue Monitor" },
    ],
  },
  { id: "runWorkflow", label: "Run Workflow" },
  {
    id: "definitionsSubMenu",
    label: "Definitions",
    children: [
      { id: "workflowDefItem", label: "Workflow" },
      { id: "taskDefItem", label: "Task" },
      { id: "eventHandlerDefItem", label: "Event Handler" },
    ],
  },
  {
    id: "helpMenu",
    label: "Help",
    children: [
      { id: "docsItem", label: "Docs" },
      { id: "requestsItem", label: "Requests" },
      { id: "supportItem", label: "Support" },
    ],
  },
  { id: "swaggerItem", label: "API Docs" },
];

/** Collect ids in tree order (depth-first) for a given root. */
function collectIds(items: SidebarItem[]): string[] {
  const ids: string[] = [];
  for (const item of items) {
    ids.push(item.id);
    if (item.children) ids.push(...collectIds(item.children));
  }
  return ids;
}

const rootOrder = collectIds(baseSidebar);
const childOrderByParent = new Map<string, string[]>();
for (const item of baseSidebar) {
  if (item.children) {
    childOrderByParent.set(item.id, collectIds(item.children));
  }
}

/**
 * Map (targetMenu, position) from plugin API to before/after for createSidebar.
 * Uses base sidebar order so position N means "after the (N-1)th item" or "before first" for 0.
 */
function positionToAnchor(
  targetMenu: SidebarMenuTarget,
  position: "start" | "end" | number | undefined,
): { before?: string; after?: string } {
  const order =
    targetMenu === "root"
      ? rootOrder
      : (childOrderByParent.get(targetMenu) ?? []);
  const pos = position ?? "end";

  if (pos === "start" || (typeof pos === "number" && pos <= 0)) {
    return order.length ? { before: order[0] } : {};
  }
  if (pos === "end") {
    return order.length ? { after: order[order.length - 1] } : {};
  }
  const index = typeof pos === "number" ? pos : 0;
  if (index <= 0) return order.length ? { before: order[0] } : {};
  const afterIndex = Math.min(index - 1, order.length - 1);
  return { after: order[afterIndex] };
}

/**
 * Convert a plugin SidebarItemRegistration to SidebarMenuExtension (before/after).
 * Preserves nested items (e.g. adminSubMenu with children).
 */
export function registrationToExtension(
  reg: SidebarItemRegistration,
): SidebarMenuExtension {
  const anchor = positionToAnchor(reg.targetMenu, reg.position);
  return {
    id: reg.id,
    label: reg.title,
    ...anchor,
    children: reg.items?.map(registrationToExtension),
  };
}

/**
 * Find the parent array and index of the node with the given id (depth-first search).
 * Returns null if not found.
 */
function findInTree(
  root: SidebarItem[],
  id: string,
): { array: SidebarItem[]; index: number } | null {
  for (let i = 0; i < root.length; i++) {
    if (root[i].id === id) {
      return { array: root, index: i };
    }
    if (root[i].children) {
      const found = findInTree(root[i].children!, id);
      if (found) return found;
    }
  }
  return null;
}

function extensionToItem(ext: SidebarMenuExtension): SidebarItem {
  return {
    id: ext.id,
    label: ext.label,
    children: ext.children?.map(extensionToItem),
  };
}

function cloneTree(items: SidebarItem[]): SidebarItem[] {
  return items.map((item) => ({
    ...item,
    children: item.children ? cloneTree(item.children) : undefined,
  }));
}

/**
 * Merge extensions into the base sidebar tree using before/after anchors.
 * Each extension is inserted after the node with id === ext.after, or before the node with id === ext.before, or appended if neither is set.
 */
export function createSidebar(
  base: SidebarItem[],
  extensions: SidebarMenuExtension[] = [],
): SidebarItem[] {
  const result = cloneTree(base);

  for (const ext of extensions) {
    const item = extensionToItem(ext);

    if (ext.after !== undefined) {
      const found = findInTree(result, ext.after);
      if (found) {
        found.array.splice(found.index + 1, 0, item);
      } else {
        result.push(item);
      }
    } else if (ext.before !== undefined) {
      const found = findInTree(result, ext.before);
      if (found) {
        found.array.splice(found.index, 0, item);
      } else {
        result.push(item);
      }
    } else {
      result.push(item);
    }
  }

  return result;
}
