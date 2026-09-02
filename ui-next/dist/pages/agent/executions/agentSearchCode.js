const g=({start:i,size:n,sort:t,freeText:o,query:r,classifier:a,topLevelOnly:e})=>`${window.location.origin}/api/workflow/search?${new URLSearchParams({start:String(i),size:String(n),sort:t,freeText:o,query:r,classifier:a,topLevelOnly:String(e)}).toString()}`;export{g as buildEndpoint};
//# sourceMappingURL=agentSearchCode.js.map
