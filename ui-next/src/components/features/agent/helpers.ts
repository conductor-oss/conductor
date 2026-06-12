export const testWorkflowDefOrExecutionViewPathname = (pathname: string) => {
  return (
    /^\/workflowDef\/.*$/.test(pathname) ||
    /^\/execution\/.*$/.test(pathname) ||
    pathname.startsWith("/newWorkflowDef")
  );
};
