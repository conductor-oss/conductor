import { useEnv } from "plugins/env";
import { useNavigate } from "react-router";
import Url from "url-parse";

export function useReplaceHistory() {
  const navigate = useNavigate();
  const { stack, defaultStack } = useEnv();

  return (path: string) => {
    const url = new Url(path, {}, true);
    if (stack !== defaultStack) {
      url.query.stack = stack;
    }

    navigate(url.toString(), { replace: true });
  };
}
