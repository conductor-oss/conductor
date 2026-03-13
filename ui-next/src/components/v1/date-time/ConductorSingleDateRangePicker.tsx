import { Box } from "@mui/material";
import { ReactNode, useState } from "react";
import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import { getEndOfDayTime, getStartOfDayTime } from "utils/date";
import "./CustomDateRangePicker.scss";

export interface SingleDateRangePickerProps {
  fromDate: string;
  toDate: string;
  setStartTime: (data: string) => void;
  setEndTime: (data: string) => void;
  maxDate?: boolean;
}

export const SingleDateRangePicker = ({
  fromDate,
  toDate,
  setStartTime,
  setEndTime,
  maxDate,
}: SingleDateRangePickerProps) => {
  const [localStartDate, setLocalStartDate] = useState<Date | null>(
    fromDate ? new Date(Number(fromDate)) : null,
  );
  const [localEndDate, setLocalEndDate] = useState<Date | null>(
    fromDate && toDate ? new Date(Number(toDate)) : null,
  );

  const onChange = (dates: [Date | null, Date | null] | null) => {
    if (dates) {
      const [start, end] = dates;
      setLocalStartDate(start);
      setLocalEndDate(end);

      const startTimeStamp = String(getStartOfDayTime(start));
      setStartTime(startTimeStamp);

      if (end) {
        const endTimeStamp = String(getEndOfDayTime(end));
        setEndTime(endTimeStamp);
      } else {
        // if there's no end, it means that the range selection has re-started,
        // meaning it's the first click of the 2-clicks process
        const endTimeStamp = String(getEndOfDayTime(start));
        setEndTime(endTimeStamp);
      }
    }
  };

  const formatWeekDay = (day: string): ReactNode => {
    return day.charAt(0);
  };

  return (
    <Box
      sx={{
        userSelect: "none",
      }}
    >
      <DatePicker
        selected={localStartDate}
        onChange={onChange}
        startDate={localStartDate}
        endDate={localEndDate}
        selectsRange
        inline
        formatWeekDay={formatWeekDay}
        maxDate={
          (!localStartDate || localEndDate) && maxDate ? new Date() : null
        }
      />
    </Box>
  );
};
