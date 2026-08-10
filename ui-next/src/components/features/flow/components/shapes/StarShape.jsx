function StarShape() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      xmlnsXlink="http://www.w3.org/1999/xlink"
      width="100%"
      height="100%"
      viewBox="0 0 278 276"
      preserveAspectRatio="none"
    >
      <defs>
        <path
          id="path-tk79k9osx6-1"
          d="M674.079 313.089l113.137 113.137c4.668 4.588 7.118 9.272 7.35 14.053.231 4.78-2.219 9.525-7.351 14.232L674.077 567.648c-5.185 4.12-9.7 6.34-13.543 6.662-3.843.322-8.756-1.898-14.74-6.662L532.655 454.511c-4.73-6.095-7.14-11.2-7.237-15.313-.09-4.114 2.321-8.437 7.237-12.97L645.793 313.09c5.348-4.67 10.073-7.14 14.173-7.41 4.1-.271 8.804 2.198 14.113 7.409z"
        ></path>
        <filter
          id="filter-tk79k9osx6-2"
          width="104.5%"
          height="104.5%"
          x="-2.2%"
          y="-2.2%"
          filterUnits="objectBoundingBox"
        >
          <feOffset in="SourceAlpha" result="shadowOffsetOuter1"></feOffset>
          <feGaussianBlur
            in="shadowOffsetOuter1"
            result="shadowBlurOuter1"
            stdDeviation="2"
          ></feGaussianBlur>
          <feColorMatrix
            in="shadowBlurOuter1"
            values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.5 0"
          ></feColorMatrix>
        </filter>
      </defs>
      <g fill="none" fillRule="evenodd" stroke="none" strokeWidth="1">
        <g transform="translate(-521 -302)">
          <use
            fill="#000"
            filter="url(#filter-tk79k9osx6-2)"
            xlinkHref="#path-tk79k9osx6-1"
          ></use>
          <use fill="#FFF" xlinkHref="#path-tk79k9osx6-1"></use>
        </g>
      </g>
    </svg>
  );
}

export default StarShape;
