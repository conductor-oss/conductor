import React from "react";
import { Tab, Tabs, NavLink } from "../../components";

export default function SearchTabs({ tabIndex }) {
  return (
    <Tabs value={tabIndex}>
      <Tab label="Workflows" component={NavLink} path="/" />
      <Tab label="Tasks" component={NavLink} path="/search/tasks" />
    </Tabs>
  );
}
