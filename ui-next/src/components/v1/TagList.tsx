import { Box } from "@mui/material";
import { TagDto } from "../../types/Tag";
import TagChip from "../TagChip";

interface TagListProps {
  tags?: TagDto[];
  name: string;
  sx?: Record<string, unknown>;
  style?: Record<string, unknown>;
}

export const TagList = ({
  tags,
  name,
  sx = { mr: 2, mt: 1 },
  style,
}: TagListProps) => {
  if (!tags?.length) return null;

  return (
    <Box>
      {tags.map((tag) => {
        if (!tag) return null;
        const { key, value } = tag;
        return (
          <TagChip
            style={style}
            key={`${name}-${key}-${value}`}
            sx={sx}
            label={`${key}:${value}`}
          />
        );
      })}
    </Box>
  );
};

export default TagList;

export const TagsRenderer = <T extends { name: string }>(
  tags: TagDto[],
  row: T,
) => <TagList tags={tags} name={row?.name} />;
