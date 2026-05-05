import {
  Box,
  IconButton,
  Paper,
  Typography,
  Button,
  Alert,
  Fade,
} from "@mui/material";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import KeyIcon from "@mui/icons-material/Key";
import CloseIcon from "@mui/icons-material/Close";
import { ReactElement, useState } from "react";
import { ActorRef } from "xstate";
import { useSelector } from "@xstate/react";
import {
  AccessKey,
  CreateAndDisplayApplicationEvents,
  CreateAndDisplayApplicationMachineEventTypes,
} from "./state/types";
import { LibraryBooks } from "@mui/icons-material";
import { logrocketTrackIfEnabled } from "utils/logrocket";

interface MetadataBannerStatelessProps {
  installScript?: string;
  readme?: string;
  isDisplayKeys: boolean;
  isErrorCreatingApp?: boolean;
  applicationAccessKey: AccessKey; // Using 'any' here since the type isn't clear from the original code
  onCopy: () => void;
  onClose?: () => void;
  KeysDisplayerComponent: (props: {
    onClose: () => void;
    accessKeys: AccessKey;
  }) => ReactElement;
  onGetAccessKey: () => void;
  onRecreateKeys: () => void;
  onCloseKeysDialog: () => void;
  errorCreatingAppMessage?: string;
}

export const MetadataBannerStateless = ({
  installScript,
  isDisplayKeys,
  applicationAccessKey,
  readme,
  onCopy,
  onClose,
  onGetAccessKey,
  onRecreateKeys,
  onCloseKeysDialog,
  KeysDisplayerComponent,
  isErrorCreatingApp,
  errorCreatingAppMessage,
}: MetadataBannerStatelessProps) => {
  return installScript == null ? null : (
    <Paper
      elevation={0}
      sx={{
        p: 3,
        mb: 2,
        backgroundColor: "#F8FAFF",
        borderRadius: 1,
        border: "1px solid",
        borderColor: "#8EC5FF",
        position: "relative",
      }}
    >
      {onClose != null ? (
        <IconButton
          size="small"
          onClick={onClose}
          sx={{
            position: "absolute",
            right: 8,
            top: 8,
            color: "#505050",
          }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
      ) : null}
      <Typography
        variant="h6"
        sx={{
          fontSize: "16px",
          fontWeight: 600,
          mb: 2,
        }}
      >
        Local Workers Needed
      </Typography>

      <Typography
        variant="body2"
        sx={{
          color: "#505050",
          mb: 2,
        }}
      >
        1. You need to run local workers to try out this workflow. Copy/Paste
        this Command into your Terminal.
      </Typography>

      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          backgroundColor: "rgb(32, 43, 88)",
          borderRadius: "4px",
          p: 1.5,
          color: "white",
          gap: 1,
        }}
      >
        <Typography
          sx={{
            fontFamily: "monospace",
            fontSize: "14px",
            flexGrow: 1,
            whiteSpace: "nowrap",
            overflow: "auto",
            "&::-webkit-scrollbar": {
              display: "none",
            },
            msOverflowStyle: "none",
            scrollbarWidth: "none",
          }}
        >
          {installScript || "$"}
        </Typography>
        <IconButton
          size="small"
          id="copy-install-script"
          onClick={onCopy}
          sx={{
            color: "white",
            "&:hover": {
              backgroundColor: "rgba(255, 255, 255, 0.1)",
            },
          }}
        >
          <ContentCopyIcon fontSize="small" />
        </IconButton>
      </Box>
      <Typography
        variant="body2"
        sx={{
          color: "#505050",
          mt: 2,
          mb: 2,
        }}
      >
        2. When prompted, enter your Access ID + Key from the button below.
      </Typography>
      <Box sx={{ display: "flex", justifyContent: "space-between", mt: 2 }}>
        {applicationAccessKey == null ? (
          <Button
            startIcon={<KeyIcon />}
            variant="contained"
            onClick={onGetAccessKey}
            id="get-access-key-install-script"
          >
            Get Access Keys
          </Button>
        ) : (
          <Button
            startIcon={<KeyIcon />}
            variant="contained"
            onClick={onRecreateKeys}
          >
            Recreate Keys
          </Button>
        )}
        {readme ? (
          <Button
            component="a"
            href={readme}
            target="_blank"
            variant="text"
            color="primary"
            startIcon={<LibraryBooks />}
          >
            Learn More
          </Button>
        ) : null}
      </Box>
      {isDisplayKeys && (
        <KeysDisplayerComponent
          onClose={onCloseKeysDialog}
          accessKeys={applicationAccessKey}
        />
      )}
      <Fade in={!!isErrorCreatingApp} timeout={400} unmountOnExit>
        <div>
          <Alert severity="error" sx={{ mt: 2 }}>
            {errorCreatingAppMessage}
          </Alert>
        </div>
      </Fade>
    </Paper>
  );
};

interface MetadataBannerProps {
  createAndDisplayAppActor: ActorRef<CreateAndDisplayApplicationEvents>;
  KeysDisplayerComponent: (props: {
    onClose: () => void;
    accessKeys: AccessKey;
  }) => ReactElement;
  onClose?: () => void;
  installScript?: string;
  readme?: string;
}

export const MetadataBanner = ({
  createAndDisplayAppActor: metadataEditorActor,
  onClose,
  installScript,
  readme,
  KeysDisplayerComponent,
}: MetadataBannerProps) => {
  const isDisplayKeys = useSelector(metadataEditorActor, (state) =>
    state.hasTag("displayKeys"),
  );
  const isErrorCreatingApp = useSelector(metadataEditorActor, (state) =>
    state.hasTag("displayError"),
  );
  const errorCreatingAppMessage = useSelector(
    metadataEditorActor,
    (state) => state.context.errorCreatingAppMessage,
  );
  const applicationAccessKey = useSelector(
    metadataEditorActor,
    (state) => state.context.applicationAccessKey,
  );

  const [, setCopied] = useState(false);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(installScript || "");
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
      logrocketTrackIfEnabled("user_copy_install_script", { installScript });
    } catch (err) {
      console.error("Failed to copy text: ", err);
    }
  };

  const handleGetAccessKey = () => {
    metadataEditorActor.send({
      type: CreateAndDisplayApplicationMachineEventTypes.CREATE_APPLICATION,
    });

    logrocketTrackIfEnabled("user_created_access_key_in_metadata_banner");
  };

  const handleCloseKeysDialog = () => {
    metadataEditorActor.send({
      type: CreateAndDisplayApplicationMachineEventTypes.CLOSE_KEYS_DIALOG,
    });
  };

  const handleRecreateKeys = () => {
    metadataEditorActor.send({
      type: CreateAndDisplayApplicationMachineEventTypes.RECREATE_KEYS,
    });
  };

  return (
    <MetadataBannerStateless
      readme={readme}
      installScript={installScript}
      isDisplayKeys={isDisplayKeys}
      applicationAccessKey={applicationAccessKey}
      onCopy={handleCopy}
      onClose={onClose}
      onGetAccessKey={handleGetAccessKey}
      onRecreateKeys={handleRecreateKeys}
      onCloseKeysDialog={handleCloseKeysDialog}
      KeysDisplayerComponent={KeysDisplayerComponent}
      isErrorCreatingApp={isErrorCreatingApp}
      errorCreatingAppMessage={errorCreatingAppMessage}
    />
  );
};
