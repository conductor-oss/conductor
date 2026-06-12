import React from "react";
import { GitFork } from "@phosphor-icons/react";

export const ForkJoinIcon = () =>
  React.createElement(
    "div",
    {
      style: {
        transform: "rotate(180deg)",
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
      },
    },
    React.createElement(GitFork, { size: 24 }),
  );
