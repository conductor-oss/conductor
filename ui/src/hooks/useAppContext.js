import { useContext } from "react";
import AppContext from "../components/context/AppContext";

export default function useAppContext() {
  return useContext(AppContext);
}
