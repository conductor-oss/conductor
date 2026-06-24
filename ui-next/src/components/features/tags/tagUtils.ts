export const isValidTag = (value: string) => /^[^:]+:[^:]+$/.test(value.trim());
