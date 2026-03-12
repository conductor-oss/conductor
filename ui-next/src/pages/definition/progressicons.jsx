const style = {
  stroke: "#0971f1",
  strokeDasharray: 93,
  strokeDashoffset: 93,
  strokeWidth: 2,
  fill: "transparent",
};
export default function ProgressIcon() {
  return (
    <svg width="30px" height="30px">
      <path style={style} d="M15,15 m0,-8 a 8,8 0 0,1 0,16 a 8,8 0 0,1 0,-16">
        <animate
          attributeName="stroke-dashoffset"
          dur="10s"
          to="0"
          repeatCount="0"
        />
      </path>
    </svg>
  );
}
