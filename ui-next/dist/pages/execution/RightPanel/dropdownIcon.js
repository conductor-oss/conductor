import{TaskStatus as a}from"../../../types/TaskStatus.js";function c(r){let e;switch(r){case a.COMPLETED:e="✅";break;case a.COMPLETED_WITH_ERRORS:e="❗";break;case a.CANCELED:e="🛑";break;case a.IN_PROGRESS:case a.SCHEDULED:e="⌛";break;case a.TIMED_OUT:e="⛔";break;case a.FAILED:e="❗";break;default:e="❌"}return e+" "}export{c as dropdownIcon};
//# sourceMappingURL=dropdownIcon.js.map
