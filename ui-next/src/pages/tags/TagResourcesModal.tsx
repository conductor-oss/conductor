import React from "react";
import {
  Box,
  Chip,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  InputBase,
  List,
  ListItem,
  ListItemButton,
  Typography,
} from "@mui/material";
import {
  ArrowRight,
  Article,
  Broadcast,
  CalendarBlank,
  ChatText,
  Code,
  Cube,
  FileText,
  GitBranch,
  Link,
  Lock,
  MagnifyingGlass,
  TreeStructure,
  WarningCircle,
  X,
  XCircle,
} from "@phosphor-icons/react";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AI_PROMPTS_MANAGEMENT_URL,
  EVENT_HANDLERS_URL,
  INTEGRATIONS_MANAGEMENT_URL,
  SCHEDULER_DEFINITION_URL,
  TASK_DEF_URL,
  WEBHOOK_ROUTE_URL,
  WORKFLOW_DEFINITION_URL,
} from "utils/constants/route";
import { useTagResources } from "utils/hooks";

interface TagResourcesModalProps {
  open: boolean;
  onClose: () => void;
  tag: string;
  resourceType: string;
  resourceLabel: string;
}

const NAVIGABLE_RESOURCE_TYPES: Record<
  string,
  ((id: string) => string) | null
> = {
  WORKFLOW_DEF: (id) => `${WORKFLOW_DEFINITION_URL.BASE}/${id}`,
  TASK_DEF: (id) => `${TASK_DEF_URL.BASE}/${id}`,
  EVENT_HANDLER: (id) => `${EVENT_HANDLERS_URL.BASE}/${id}`,
  WORKFLOW_SCHEDULE: (id) => `${SCHEDULER_DEFINITION_URL.BASE}/${id}`,
  USER_FORM_TEMPLATE: (id) => `/human/templates/${id}`,
  WEBHOOK: (id) => `${WEBHOOK_ROUTE_URL.LIST}/${id}`,
  INTEGRATION_PROVIDER: (id) =>
    INTEGRATIONS_MANAGEMENT_URL.EDIT_INTEGRATION_MODEL.replace(":id", id),
  PROMPT: (id) => `${AI_PROMPTS_MANAGEMENT_URL.BASE}/${id}`,
  SECRET_NAME: null,
  ENV_VARIABLE: null,
};

const RESOURCE_ICONS: Record<string, React.ElementType> = {
  WORKFLOW_DEF: TreeStructure,
  TASK_DEF: Cube,
  WEBHOOK: Link,
  EVENT_HANDLER: Broadcast,
  USER_FORM_TEMPLATE: FileText,
  WORKFLOW_SCHEDULE: CalendarBlank,
  SECRET_NAME: Lock,
  PROMPT: ChatText,
  ENV_VARIABLE: Code,
  INTEGRATION_PROVIDER: GitBranch,
};

const SEARCH_THRESHOLD = 8;

export default function TagResourcesModal({
  open,
  onClose,
  tag,
  resourceType,
  resourceLabel,
}: TagResourcesModalProps) {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const [tagKey, tagValue] = tag.split(/:(.+)/);

  const {
    data: resourceItems,
    isLoading,
    isError,
  } = useTagResources(
    open ? tagKey : null,
    open ? tagValue : null,
    open ? resourceType : null,
  );

  const getNavigationPath = NAVIGABLE_RESOURCE_TYPES[resourceType] ?? null;
  const ResourceIcon = RESOURCE_ICONS[resourceType] ?? Article;
  const showSearch =
    !isLoading && (resourceItems?.length ?? 0) > SEARCH_THRESHOLD;

  const filteredItems = useMemo(() => {
    if (!resourceItems) return [];
    if (!search.trim()) return resourceItems;
    const q = search.toLowerCase();
    return resourceItems.filter((item) =>
      item.displayName.toLowerCase().includes(q),
    );
  }, [resourceItems, search]);

  const handleNavigate = (id: string) => {
    if (!getNavigationPath) return;
    onClose();
    navigate(getNavigationPath(id));
  };

  const handleClose = () => {
    setSearch("");
    onClose();
  };

  return (
    <Dialog
      fullWidth
      maxWidth="sm"
      open={open}
      onClose={handleClose}
      slotProps={{
        paper: {
          sx: {
            maxHeight: 520,
            display: "flex",
            flexDirection: "column",
          },
        },
      }}
    >
      {/* ── Title ───────────────────────────────────────────── */}
      <DialogTitle sx={{ backgroundColor: "rgba(5,5,5,0.03)" }}>
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 1,
          }}
        >
          {/* Left: icon + label + tag chip */}
          <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
            <Box
              sx={{
                width: 32,
                height: 32,
                borderRadius: "8px",
                backgroundColor: "primary.50",
                border: "1px solid",
                borderColor: "primary.100",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
                color: "primary.main",
              }}
            >
              <ResourceIcon size={16} weight="duotone" />
            </Box>

            <Box>
              <Typography
                component="span"
                sx={{ fontWeight: 600, fontSize: "14px", display: "block" }}
              >
                {resourceLabel}
              </Typography>
              <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                <Typography
                  component="span"
                  sx={{ fontSize: "12px", color: "grey", mr: 1 }}
                >
                  tagged with
                </Typography>
                <Chip
                  label={tag}
                  variant="outlined"
                  size="small"
                  sx={{
                    height: 22,
                    fontSize: "11px",
                    fontWeight: 500,
                    backgroundColor: "primary.50",
                    borderColor: "primary.200",
                    color: "primary.700",
                    "& .MuiChip-label": { px: 0.75 },
                  }}
                />
              </Box>
            </Box>
          </Box>

          {/* Right: count + close */}
          <Box
            sx={{ display: "flex", alignItems: "center", gap: 0.5, ml: "auto" }}
          >
            {!isLoading && resourceItems && resourceItems.length > 0 && (
              <Chip
                label={resourceItems.length}
                size="small"
                sx={{
                  height: 20,
                  fontSize: "11px",
                  fontWeight: 600,
                  backgroundColor: "grey.100",
                  color: "text.secondary",
                  "& .MuiChip-label": { px: 1 },
                }}
              />
            )}
            <IconButton
              size="small"
              onClick={handleClose}
              sx={{ color: "text.secondary" }}
            >
              <X size={15} />
            </IconButton>
          </Box>
        </Box>
      </DialogTitle>

      {/* ── Search (appears only when list is long) ──────────── */}
      {showSearch && (
        <>
          <Box
            sx={{
              mx: 2,
              px: 3,
              py: 1,
              display: "flex",
              alignItems: "center",
              gap: 1,
              borderBottom: "1px solid",
              borderColor: "divider",
            }}
          >
            <Box sx={{ display: "flex" }}>
              <MagnifyingGlass size={14} />
            </Box>
            <InputBase
              autoFocus
              fullWidth
              placeholder={`Search ${resourceLabel.toLowerCase()}…`}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              sx={{ fontSize: "14px" }}
            />
            {search && (
              <IconButton
                size="small"
                onClick={() => setSearch("")}
                sx={{ color: "text.disabled", p: 0.25 }}
              >
                <XCircle size={14} />
              </IconButton>
            )}
          </Box>
        </>
      )}

      {/* ── Content ─────────────────────────────────────────── */}
      <DialogContent sx={{ p: 0, flex: 1, overflowY: "auto" }}>
        {/* Loading */}
        {isLoading && (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              gap: 1.5,
              py: 6,
            }}
          >
            <CircularProgress size={22} thickness={3} />
            <Typography variant="body2" color="text.disabled">
              Loading…
            </Typography>
          </Box>
        )}

        {/* Error */}
        {isError && (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: 1,
              py: 6,
              color: "error.main",
            }}
          >
            <WarningCircle size={26} weight="duotone" />
            <Typography variant="body1" color="error.main">
              Failed to load. Please try again.
            </Typography>
          </Box>
        )}

        {/* Empty */}
        {!isLoading && !isError && resourceItems?.length === 0 && (
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: 1,
              py: 6,
              color: "text.disabled",
            }}
          >
            <Article size={26} weight="duotone" />
            <Typography variant="body1" color="text.disabled">
              No {resourceLabel.toLowerCase()} with this tag.
            </Typography>
          </Box>
        )}

        {/* No search results */}
        {!isLoading &&
          !isError &&
          resourceItems &&
          resourceItems.length > 0 &&
          filteredItems.length === 0 && (
            <Box
              sx={{
                display: "flex",
                justifyContent: "center",
                py: 5,
              }}
            >
              <Typography variant="body1" color="text.disabled">
                No results for &ldquo;{search}&rdquo;
              </Typography>
            </Box>
          )}

        {/* List */}
        {!isLoading && !isError && filteredItems.length > 0 && (
          <List sx={{ padding: "10px" }}>
            {filteredItems.map(({ id, displayName }, index) =>
              getNavigationPath ? (
                <ListItemButton
                  key={id}
                  onClick={() => handleNavigate(id)}
                  sx={{
                    px: 4,
                    py: 1.75,
                    borderBottom:
                      index < filteredItems.length - 1 ? "1px solid" : "none",
                    borderColor: "divider",
                    "&:hover .nav-arrow": {
                      opacity: 1,
                      transform: "translateX(3px)",
                    },
                    "&:hover .item-label": {
                      color: "primary.main",
                    },
                  }}
                >
                  <Typography
                    className="item-label"
                    variant="body1"
                    sx={{ transition: "color 0.15s ease" }}
                  >
                    {displayName}
                  </Typography>
                  <Box
                    className="nav-arrow"
                    sx={{
                      color: "primary.main",
                      opacity: 0,
                      transition: "opacity 0.15s ease, transform 0.15s ease",
                      display: "flex",
                      ml: "auto",
                      flexShrink: 0,
                    }}
                  >
                    <ArrowRight size={14} weight="bold" />
                  </Box>
                </ListItemButton>
              ) : (
                <ListItem
                  key={id}
                  sx={{
                    px: 4,
                    py: 1.75,
                    borderBottom:
                      index < filteredItems.length - 1 ? "1px solid" : "none",
                    borderColor: "divider",
                  }}
                >
                  <Typography variant="body1">{displayName}</Typography>
                </ListItem>
              ),
            )}
          </List>
        )}
      </DialogContent>
    </Dialog>
  );
}
