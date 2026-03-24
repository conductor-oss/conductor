import React from "react";
import EmptyPageIntro from "./EmptyPageIntro";
import { openInNewTab } from "utils/helpers";
import UnlockIcon from "./v1/icons/UnlockIcon";

const TALK_TO_AN_EXPERT_URL = "https://orkes.io/talk-to-an-expert";

const FeatureDisabledComponent = ({
  image,
  title,
  message,
}: {
  image?: string;
  title?: string;
  message?: string;
}) => {
  return (
    <EmptyPageIntro
      image={image}
      title={title || ""}
      variant="featureDisabled"
      message={message || "This feature is only available as an Add-On."}
      primaryAction={{
        text: "Talk to an expert",
        onClick: () => openInNewTab(TALK_TO_AN_EXPERT_URL),
        startIcon: <UnlockIcon size={20} />,
      }}
    />
  );
};

export default FeatureDisabledComponent;
