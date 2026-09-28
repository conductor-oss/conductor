import slackLogoUrl from "images/svg/slack-logo-transparent.svg";
import type { ImgHTMLAttributes } from "react";

const SlackIcon = (props: ImgHTMLAttributes<HTMLImageElement>) => (
  <img src={slackLogoUrl} width="19" height="19" alt="Slack" {...props} />
);

export default SlackIcon;
