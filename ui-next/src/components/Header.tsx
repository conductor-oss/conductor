import LinearProgress from "components/LinearProgress";

export default function Header({ loading }: { loading: boolean }) {
  return <div>{loading && <LinearProgress id="linear-progress" />}</div>;
}
