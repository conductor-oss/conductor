package com.netflix.conductor.core.events;

import com.netflix.conductor.common.metadata.events.EventHandler;
import java.util.Map;

public interface ActionProcessor {

  Map<String, Object> execute(EventHandler.Action action, Object payloadObject, String event, String messageId);

}
