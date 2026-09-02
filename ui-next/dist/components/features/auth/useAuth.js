import{useContext as r}from"react";import{AuthContext as o}from"./context.js";import{defaultAuthState as u}from"./types.js";const f=()=>{const{authService:e,authState:t}=r(o);return t??{...u,authService:e}};export{f as useAuth};
//# sourceMappingURL=useAuth.js.map
