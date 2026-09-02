import{jsx as r,jsxs as t}from"react/jsx-runtime";import{SafariWarning as p}from"./SafariWarning.js";import d from"./features/OnboardingQuiz.js";import{useState as l}from"react";import{Helmet as c}from"react-helmet";import{Outlet as g}from"react-router";import{AuthProvider as f}from"./features/auth/AuthProvider.js";import u from"./layout/SideAndTopBarsLayout.js";import{SidebarProvider as w}from"./providers/sidebar/context/SidebarContextProvider.js";import{UserSettingsProvider as h}from"../shared/UserSettingsProvider.js";import{pluginRegistry as A}from"../plugins/registry/registry.js";import"@mui/x-date-pickers/AdapterDateFns";import"date-fns";import"date-fns-tz";import"lodash";import"lodash/isEmpty";import{featureFlags as o,FEATURES as i}from"../utils/flags.js";import{GTAG_LABEL as S}from"../utils/gtag.js";import"../utils/helpers.js";import"../utils/logger.js";import"lodash/isPlainObject";import"lodash/isArray";import{useAPIReleaseVersion as j}from"../utils/query.js";import"../utils/roles.js";import"lodash/lowerCase";import"lodash/upperFirst";import{isSafari as y}from"../utils/utils.js";import"../utils/task.js";import"lodash/pickBy";import"lodash/isNil";import"../utils/tracker.js";import"./providers/messageContext/MessageContext.js";import"@mui/material";import"./ui/MuiAlert.js";import"@phosphor-icons/react";import"react-query";import"../utils/workflow.js";import{getThemeAsCSSVariables as L}from"../utils/themeVariables.js";const b=A.getGlobalComponents(),v=f,x=o.isEnabled(i.SHOW_ONBOARDING_QUIZ),N=o.isEnabled(i.PLAYGROUND);function lr(){j({option:{enabled:!0}});const[e,m]=l(y),s=L();return r(v,{children:t(h,{children:[r("style",{children:`
          :root {
            ${s.join(`
`)}
          }
        `}),x?r(d,{}):null,r(w,{children:t(u,{children:[e&&r(p,{setShowSafariWarning:m}),r(g,{})]})}),b.map((a,n)=>r(a,{},n)),N?t(c,{children:[r("script",{nonce:"tpsHAxwU5x0csoIuLNs2vg==",children:`
              (function(w, d, s, l, i) {
                w[l] = w[l] || [];
                w[l].push({ "gtm.start": new Date().getTime(), event: "gtm.js" });
                var f = d.getElementsByTagName(s)[0],
                    j = d.createElement(s),
                    dl = l != "dataLayer" ? "&l=" + l : "";
                j.async = true;
                j.src = "https://www.googletagmanager.com/gtm.js?id=" + i + dl;
                f.parentNode.insertBefore(j, f);
              })(window, document, "script", "dataLayer", "GTM-TD98B55Q");
            `}),r("script",{type:"text/javascript",id:"hs-script-loader",async:!0,defer:!0,src:"//js.hs-scripts.com/20882608.js"}),r("script",{async:!0,src:"https://www.googletagmanager.com/gtag/js?id=G-6DLM7JND12"}),r("script",{nonce:"tpsHAxwU5x0csoIuLNs2vg==",children:`window.dataLayer = window.dataLayer || [];
                function gtag(){dataLayer.push(arguments);}
                gtag('js', new Date());

                gtag('config', '${S}');`})]}):null]})})}export{lr as App};
//# sourceMappingURL=App.js.map
