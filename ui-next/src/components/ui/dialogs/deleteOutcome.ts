/**
 * The line under the version picker: what the definition looks like once the delete lands.
 *
 * Removing one version of several is the case people read wrong — the row disappears from
 * the list and reappears at an older version — so the dialog states what survives rather
 * than only what goes.
 */

const nameThem = (versions: number[]) =>
  versions.length === 1
    ? `Version ${versions[0]}`
    : `Versions ${versions.slice(0, -1).join(", ")} and ${
        versions[versions.length - 1]
      }`;

export const describeOutcome = (
  /** Every known version, newest first. */
  versions: number[],
  /** The version being deleted; `undefined` deletes the whole definition. */
  version: number | undefined,
  entityLabel: string,
): string => {
  if (version === undefined) {
    return `Nothing will remain — the ${entityLabel} itself is removed.`;
  }

  const remaining = versions.filter((each) => each !== version);
  if (remaining.length === 0) {
    return `This is the only version, so the ${entityLabel} is removed.`;
  }

  const deletingLatest = version === versions[0];

  // Past three, naming them all is noise; a count reads faster.
  const survivors =
    remaining.length > 3
      ? `${remaining.length} ${
          deletingLatest ? "earlier" : "other"
        } versions will remain`
      : `${nameThem(remaining)} will remain`;

  // Only removing the newest moves "latest" onto another version.
  if (!deletingLatest) {
    return `${survivors}.`;
  }
  // With one survivor, naming it twice reads as a stutter.
  return remaining.length === 1
    ? `${survivors} and become the latest.`
    : `${survivors}, and version ${remaining[0]} becomes the latest.`;
};
