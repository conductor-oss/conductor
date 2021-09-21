import React from "react";
import data from "./sampleMovieData";
import { DataTable } from "../../components";

export default () => {
  const columns = [
    { name: "title" },
    { name: "director" },
    { name: "year" },
    { name: "plot", grow: 0.5 },
  ];

  return (
    <>
      <DataTable title="Movie List" columns={columns} data={data} />
    </>
  );
};
