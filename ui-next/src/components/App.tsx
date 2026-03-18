import { SafariWarning } from "components/SafariWarning";
import OnboardingQuiz from "components/v1/quiz/OnboardingQuiz";
import React, { useState } from "react";
import { Helmet } from "react-helmet";
import { Outlet } from "react-router";
import { AuthProvider as AuthProviderImport } from "shared/auth/AuthProvider";
import SideAndTopBarsLayout from "shared/SideAndTopBarsLayout";
import { SidebarProvider } from "components/Sidebar/context/SidebarContextProvider";
import { UserSettingsProvider } from "shared/UserSettingsProvider";
import { pluginRegistry } from "plugins/registry";
import {
  featureFlags,
  FEATURES,
  GTAG_LABEL,
  isSafari,
  useAPIReleaseVersion,
  useMaybeEnableLogRocket,
} from "utils";
import { getThemeAsCSSVariables } from "utils/themeVariables";

// Resolve global components once at module load time (after plugins are registered)
const globalComponents = pluginRegistry.getGlobalComponents();

const AuthProvider = AuthProviderImport as React.ComponentType<{
  children: React.ReactNode;
}>;

const showOnboardingQuiz = featureFlags.isEnabled(
  FEATURES.SHOW_ONBOARDING_QUIZ,
);

const isPlayground = featureFlags.isEnabled(FEATURES.PLAYGROUND);

// App component that will be used as the root element
export function App() {
  useAPIReleaseVersion({ option: { enabled: true } });
  useMaybeEnableLogRocket();

  // Checking responsive width (Mobile)
  const [showSafariWarning, setShowSafariWarning] = useState(isSafari);

  const themeAsCSSVariables = getThemeAsCSSVariables();

  return (
    <AuthProvider>
      <UserSettingsProvider>
        <style>{`
          :root {
            ${themeAsCSSVariables.join("\n")}
          }
        `}</style>

        {showOnboardingQuiz ? <OnboardingQuiz /> : null}

        <SidebarProvider>
          <SideAndTopBarsLayout>
            {showSafariWarning && (
              <SafariWarning {...{ setShowSafariWarning }} />
            )}
            <Outlet />
          </SideAndTopBarsLayout>
        </SidebarProvider>

        {/* Global plugin components (e.g. pollers, invisible side-effect components) */}
        {globalComponents.map((Component, i) => (
          <Component key={i} />
        ))}
        {isPlayground ? (
          <Helmet>
            <script nonce="tpsHAxwU5x0csoIuLNs2vg==">
              {`
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
            `}
            </script>
            <script
              type="text/javascript"
              id="hs-script-loader"
              async={true}
              defer={true}
              src="//js.hs-scripts.com/20882608.js"
            />

            <script
              async
              src="https://www.googletagmanager.com/gtag/js?id=G-6DLM7JND12"
            />
            <script nonce="tpsHAxwU5x0csoIuLNs2vg==">
              {`window.dataLayer = window.dataLayer || [];
                function gtag(){dataLayer.push(arguments);}
                gtag('js', new Date());

                gtag('config', '${GTAG_LABEL}');`}
            </script>
          </Helmet>
        ) : null}
      </UserSettingsProvider>
    </AuthProvider>
  );
}
