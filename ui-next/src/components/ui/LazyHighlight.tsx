import { lazy, ReactNode, Suspense } from "react";

// react-highlight imports all of highlight.js (~1.5 MB minified), so load it
// on first use instead of in the main chunk. Until then, show the same markup
// react-highlight renders, unhighlighted.
const Highlight = lazy(() => import("react-highlight"));

type LazyHighlightProps = {
  className?: string;
  children: ReactNode;
};

export const LazyHighlight = ({ className, children }: LazyHighlightProps) => (
  <Suspense
    fallback={
      <pre>
        <code className={className}>{children}</code>
      </pre>
    }
  >
    <Highlight className={className}>{children}</Highlight>
  </Suspense>
);
