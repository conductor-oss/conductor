/**
 * SideAndTopBarsLayout
 *
 * Selects the layout component from the plugin registry.
 * - Enterprise build: uses AgentLayout (registered by the `additional` plugin)
 * - OSS build: falls back to BaseLayout (no AI agent dependencies)
 */

import { ReactNode } from "react";
import { pluginRegistry } from "plugins/registry";
import { BaseLayout } from "shared/BaseLayout";

type Props = {
  children: ReactNode;
};

const SideAndTopBarsLayout = ({ children }: Props) => {
  const Layout = pluginRegistry.getAppLayout() ?? BaseLayout;
  return <Layout>{children}</Layout>;
};

export default SideAndTopBarsLayout;
