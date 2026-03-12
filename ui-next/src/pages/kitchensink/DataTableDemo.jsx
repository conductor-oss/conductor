import { DataTable } from "../../components";
import data from "./sampleMovieData";

const DataTableDemo = () => {
  const columns = [
    { id: "title", name: "title", label: "Title" },
    { id: "director", name: "director", label: "Director" },
    { id: "year", name: "year", label: "Year" },
    { id: "plot", name: "plot", label: "Plot", grow: 0.5 },
  ];

  return (
    <>
      <DataTable title="Movie List" columns={columns} data={data} />
    </>
  );
};

export default DataTableDemo;
