export type EventExecutionDto = {
  name: string;
  event: string;
  numberOfMessages: number;
  numberOfActions: number;
  active: boolean;
};

export type EventExecutionResult = {
  results: EventExecutionDto[];
  totalHits: number;
};

export interface EventItem {
  id: string;
  messageId: string;
  name: string;
  event: string;
  created: number;
  status: string;
  action: string;
  payload: {
    name: string;
  };
  fullMessagePayload: {
    _headers: Record<string, unknown>;
    name: string;
    id: string;
    message: string;
    event: string;
    _receiverHost: string | null;
  };
  statusDescription: string;
}

export interface GroupedEventItem extends EventItem {
  groupedItems: EventItem[];
}

export type ModalConfig = {
  payload: any;
  title: string;
};
