/**
 * Builds the `sort` query parameter sent to the execution/task search APIs.
 *
 * The value must name a field the index query builder recognises. It lowercases
 * and snake_cases the field before matching it against its allow-list, and
 * silently emits no ORDER BY when there is no match — so a wrong field name
 * here shows up as unordered results rather than an error.
 */
export function buildSortParam(columnId: string, direction: string): string {
  return `${columnId}:${direction.toUpperCase()}`;
}
