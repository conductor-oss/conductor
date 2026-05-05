import type { CustomIconType } from "./types";
function ForkIcon({
  size = "24",
  color = "#000",
  flip = false,
}: CustomIconType) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      height={size}
      viewBox="0 0 398 465"
      style={flip ? { transform: "rotate(180deg)" } : {}}
    >
      <g fill="none" fillRule="evenodd" stroke="none" strokeWidth="1">
        <g fill={color} fillRule="nonzero" transform="translate(-512 -951)">
          <g transform="rotate(90 -20.976 930.58)">
            <path d="M332.06 289.59c-31.36-4.148-50.961-23.293-76.266-50.504-12.2-13.16-24.988-27.684-40.672-40.582 24.43-20.387 42.21-44.102 60.953-61.301 17.69-16.117 33.266-26.602 55.984-29.539v45.047l132.23-76.352L332.059 0v46.41c-58.47 4.656-95.375 41.613-121.36 70.227-14.316 15.68-26.898 29.609-38.711 38.359-11.988 8.766-21.473 12.773-35.227 12.984h-.051L0 167.984v61.25h.016v.016s.804-.016 2.398-.016h134.35c13.754.191 23.273 4.219 35.297 13.004 17.867 12.984 36.664 38.168 62.16 62.44 22.961 22.017 55.352 43.032 97.879 46.306v46.62l132.23-76.23L332.1 245.06l-.04 44.531z"></path>
          </g>
        </g>
      </g>
    </svg>
  );
}

export default ForkIcon;
