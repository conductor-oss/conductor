import {
  Box,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
} from "@mui/material";
import ActionButton from "components/ActionButton";
import MuiAlert from "components/MuiAlert";
import Button from "components/MuiButton";
import SaveIcon from "components/v1/icons/SaveIcon";
import XCloseIcon from "components/v1/icons/XCloseIcon";
import _differenceWith from "lodash/differenceWith";
import _uniq from "lodash/uniq";
import { useState } from "react";
import { TagDto } from "types/Tag";
import { useActionWithPath, useTags } from "utils/query";
import { getErrorMessage } from "utils/utils";
import ReplaceTagsInput from "./ReplaceTagsInput";

export type TagDialogProps = {
  open: boolean;
  itemName?: string | null;
  itemType?: string | null;
  onSuccess: () => void;
  onClose: () => void;
  tags: TagDto[];
  apiPath?: string;
};

const parsedTags = (items: string[]): TagDto[] =>
  items.map((tag: string) => {
    const [key, value] = tag.split(":");

    return {
      type: "METADATA",
      key,
      value,
    };
  });

const isTagEqual = (tag1: TagDto, tag2: TagDto): boolean =>
  tag1.key === tag2.key && tag1.value === tag2.value;

export default function AddTagDialog({
  open,
  itemName = null,
  itemType = null,
  onSuccess,
  onClose,
  tags = [],
  apiPath,
}: TagDialogProps) {
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [newTags, setNewTags] = useState<string[]>(
    tags.map((tag: TagDto) => tag && `${tag.key}:${tag.value}`),
  );
  // Only fetch all tags when the dialog is open (avoids slow /metadata/tags on every page that mounts this dialog).
  const { data: existingTags } = useTags<TagDto[]>({ enabled: open });

  const replaceTagsAction = useActionWithPath({
    onMutate: () => setLoading(true),
    onSuccess: () => {
      setErrorMessage(null);
      setLoading(false);
      onSuccess();
    },
    onError: async (response: Response) => {
      setLoading(false);

      const message = await getErrorMessage(response);
      setErrorMessage(message || "Error while updating tags.");
    },
    retry: 3,
  });

  const hasNoChanges =
    _differenceWith(tags, parsedTags(newTags), isTagEqual).length === 0 &&
    newTags.length === tags.length;

  function replaceTags(newTags: any) {
    for (const tag of newTags) {
      const tagValue = tag?.inputValue ? tag.inputValue : tag;

      if (tagValue.indexOf(":") < 0 || tagValue.split(":").length !== 2) {
        setErrorMessage(
          "Invalid tag format. Please review your tags and try again.",
        );
        return;
      }
    }

    // @ts-ignore
    replaceTagsAction.mutate({
      method: "PUT",
      path: apiPath
        ? apiPath
        : `/metadata/${itemType}/${encodeURIComponent(itemName ?? "")}/tags`,
      body: JSON.stringify(parsedTags(newTags)),
    });
  }

  return (
    <Dialog
      fullWidth
      maxWidth="sm"
      open={open}
      PaperProps={{ id: "add-tag-dialog" }}
      onClose={onClose}
    >
      <DialogTitle>Edit Tags</DialogTitle>
      <DialogContent>
        {errorMessage && (
          <Box mb={5}>
            <MuiAlert severity="error">{errorMessage}</MuiAlert>
          </Box>
        )}
        <Box
          style={{
            marginBottom: 16,
            marginTop: 15,
          }}
        >
          <ReplaceTagsInput
            label={
              <>
                Editing tags for <strong>${itemName}</strong>.
              </>
            }
            tags={tags}
            options={_differenceWith(
              existingTags,
              parsedTags(newTags),
              isTagEqual,
            )}
            onChange={(tags) => {
              setNewTags(_uniq(tags));
            }}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button
          id="cancel-save-tag-btn"
          variant="contained"
          color="secondary"
          onClick={onClose}
          startIcon={<XCloseIcon />}
        >
          Cancel
        </Button>
        <ActionButton
          id="save-tag-btn"
          variant="contained"
          color="primary"
          progress={loading}
          disabled={hasNoChanges}
          onClick={() => replaceTags(newTags)}
          startIcon={<SaveIcon />}
        >
          Save
        </ActionButton>
      </DialogActions>
    </Dialog>
  );
}
