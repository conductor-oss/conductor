import React from "react";
import { Tab, Tabs, NavLink } from "../../components";

export default function SearchTabs({ tabIndex }) {
  return (
    <Tabs value={tabIndex}>
      <Tab label="Basic Search" component={NavLink} path="/" />
      <Tab label="Find by Tasks" component={NavLink} path="/search/by-tasks" />
    </Tabs>
  );
}
