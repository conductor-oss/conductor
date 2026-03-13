import type { CustomIconType } from "./types";
function DynamicFanoutIcon({ size, color = "#000000" }: CustomIconType) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      x="0"
      y="0"
      enableBackground="new 0 0 490 490"
      version="1.1"
      viewBox="0 0 490 490"
      xmlSpace="preserve"
      width={size}
      height={size}
      fill={color}
    >
      <path d="M334.585 490L490 335.958l-91.093-97.092 33.684-69.712-92.049-47.008V0H0v221.601h50.443l62.113 31.713L334.585 490zm126.579-154.567L335.13 460.363 166.937 281.078l169.708 86.647 52.63-108.924 71.889 76.632zM20.677 20.66h299.187v180.281H20.677V20.66zm319.865 200.941v-76.25l64.647 33.004-77.975 161.366-231.325-118.12h244.653z"></path>
    </svg>
  );
}

export default DynamicFanoutIcon;
