/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.ai.video;

import java.util.Objects;

/**
 * Represents a single instruction message for video generation.
 *
 * <p>Mirrors Spring AI's {@code ImageMessage} pattern with text content and an optional weight for
 * prompt blending.
 */
public class VideoMessage {

    private String text;
    private Float weight;

    public VideoMessage(String text) {
        this(text, null);
    }

    public VideoMessage(String text, Float weight) {
        this.text = text;
        this.weight = weight;
    }

    public String getText() {
        return text;
    }

    public Float getWeight() {
        return weight;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VideoMessage that)) return false;
        return Objects.equals(text, that.text) && Objects.equals(weight, that.weight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, weight);
    }

    @Override
    public String toString() {
        return "VideoMessage{text='%s', weight=%s}".formatted(text, weight);
    }
}
