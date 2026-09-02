const r=e=>({fixed_number:{loopCondition:`(function () {
  if (${e?.taskReferenceName?`$.${e?.taskReferenceName}`:"$.do_while_ref"}['iteration'] < $.number) {
    return true;
  }
  return false;
})();`,inputParameters:{number:5},loopOver:[]},iterate_over_array:{loopCondition:`(function () {
  if (${e?.taskReferenceName?`$.${e?.taskReferenceName}`:"$.do_while_ref"}['iteration'] < $.myArray.length) {
    return true;
  }
  return false;
})();`,inputParameters:{myArray:[{name:"Orkes"},{year:2024}]},loopOver:[{name:"inline_sample",taskReferenceName:"inline_sample_ref",type:"INLINE",inputParameters:{expression:`(function () { 
  const current = $.iteration;
  return current;
})();`,evaluatorType:"graaljs",iteration:"${"+(e?.taskReferenceName?e?.taskReferenceName:"do_while_ref")+".output}"}}]}});export{r as genSampleScripts};
//# sourceMappingURL=sampleScripts.js.map
