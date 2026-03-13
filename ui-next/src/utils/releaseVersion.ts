export const releaseVersion =
  process.env?.VITE_CONDUCTOR_UI_VERSION == null
    ? "latest"
    : process.env.VITE_CONDUCTOR_UI_VERSION;
