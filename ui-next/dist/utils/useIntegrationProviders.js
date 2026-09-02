import{toMaybeQueryString as n}from"./toMaybeQueryString.js";import{INTEGRATIONS_API_URL as i}from"./constants/api.js";import{useFetch as s,STALE_TIME_DROPDOWN as u}from"./query.js";function T({category:r,activeOnly:t}){const e=n({category:r,activeOnly:t}),o=`${i.PROVIDER}${e}`;return s(o,{staleTime:u})}export{T as useIntegrationProviders};
//# sourceMappingURL=useIntegrationProviders.js.map
