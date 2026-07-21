import { Box, Link } from "@mui/material";
import { CodeSnippet } from "components/ui/CodeSnippet";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { resolveAgentGuideMarkdown } from "./guideMarkdown";

export default function AgentGuideMarkdown({ markdown }: { markdown: string }) {
  return (
    <Box
      sx={{
        width: "100%",
        maxWidth: 1000,
        mx: "auto",
        p: { xs: 3, sm: 4, md: 6 },
        color: "text.primary",
        fontSize: "14px",
        lineHeight: 1.75,
        "& h1": {
          mt: 0,
          mb: 1.5,
          color: "#111827",
          fontSize: { xs: 25, md: 30 },
          fontWeight: 700,
          letterSpacing: "-0.025em",
          lineHeight: 1.25,
        },
        "& h1 + p": {
          mt: 0,
          mb: 3,
          maxWidth: 760,
          color: "text.secondary",
          fontSize: "15px",
        },
        "& h2": {
          mt: 5,
          mb: 2,
          pb: 1.25,
          borderBottom: "1px solid",
          borderColor: "divider",
          color: "#1f2937",
          fontSize: { xs: 18, md: 20 },
          fontWeight: 700,
          letterSpacing: "-0.015em",
          lineHeight: 1.35,
        },
        "& h3": {
          mt: 3.5,
          mb: 1,
          color: "#27364a",
          fontSize: 16,
          fontWeight: 700,
        },
        "& p": { my: 1.5, color: "#374151" },
        "& ul, & ol": { pl: 3.5, color: "#374151" },
        "& li": { mb: 0.75, pl: 0.5 },
        "& blockquote": {
          m: 0,
          my: 3,
          px: 2.5,
          py: 1.5,
          border: "1px solid #f3d28b",
          borderLeft: "4px solid #d89b1d",
          borderRadius: "0 8px 8px 0",
          bgcolor: "#fff9e8",
          "& p": { m: 0, color: "#5f4614" },
        },
        "& table": {
          width: "100%",
          my: 3,
          overflow: "hidden",
          border: "1px solid",
          borderColor: "divider",
          borderCollapse: "separate",
          borderRadius: 1.5,
          borderSpacing: 0,
        },
        "& th": { bgcolor: "#f6f8fb", fontWeight: 700 },
        "& th, & td": {
          p: 1.5,
          borderBottom: "1px solid",
          borderColor: "divider",
          textAlign: "left",
        },
        "& tr:last-child td": { borderBottom: 0 },
        "& code:not(pre code)": {
          px: 0.7,
          py: 0.3,
          border: "1px solid #dbe2ea",
          borderRadius: "5px",
          bgcolor: "#f2f5f8",
          color: "#b4235a",
          fontFamily:
            'ui-monospace, "SF Mono", SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
          fontSize: "0.88em",
          fontVariantLigatures: "none",
          lineHeight: 1.5,
        },
        "& a": { fontWeight: 600 },
      }}
    >
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          pre: ({ children }) => <>{children}</>,
          code: ({ className, children, ...props }) => {
            const language = /language-([^\s]+)/.exec(className ?? "")?.[1];
            const code = String(children).replace(/\n$/, "");

            return language ? (
              <CodeSnippet code={code} className={language} variant="guide" />
            ) : (
              <code className={className} {...props}>
                {children}
              </code>
            );
          },
          a: ({ children, href }) => {
            const external = href?.startsWith("http");

            return (
              <Link
                href={href}
                target={external ? "_blank" : undefined}
                rel={external ? "noopener noreferrer" : undefined}
                underline="hover"
              >
                {children}
              </Link>
            );
          },
        }}
      >
        {resolveAgentGuideMarkdown(markdown)}
      </ReactMarkdown>
    </Box>
  );
}
