import a from"ajv";import m from"ajv-formats";const t=new a;m(t);const s=r=>{if(!r)return!1;try{return t.validateSchema(r,!0),!0}catch(e){return e.message}};export{s as isJSONSchemaValid};
//# sourceMappingURL=jsonSchema.js.map
