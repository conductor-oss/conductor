export const CONDUCTOR_SERVER_URL_TOKEN = "{{CONDUCTOR_SERVER_URL}}";

export function resolveAgentGuideMarkdown(markdown: string) {
  return markdown.replaceAll(
    CONDUCTOR_SERVER_URL_TOKEN,
    `${window.location.origin}/api`,
  );
}
