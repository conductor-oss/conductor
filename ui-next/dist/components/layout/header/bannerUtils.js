import{useMemo as s}from"react";import"@mui/x-date-pickers/AdapterDateFns";import{differenceInDays as m}from"date-fns";import"date-fns-tz";import"lodash";import"lodash/isEmpty";const u=new Date().setHours(0,0,0,0),h=(t,r,e)=>{const o=m(r,u),n=s(()=>!e,[e]);return{showBanner:r&&o>=0&&n||t,daysToGo:o}};export{u as currentDate,h as useAnnouncementBanner};
//# sourceMappingURL=bannerUtils.js.map
