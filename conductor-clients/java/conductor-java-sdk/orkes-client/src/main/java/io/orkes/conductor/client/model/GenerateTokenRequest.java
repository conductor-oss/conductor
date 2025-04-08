package io.orkes.conductor.client.model;

import lombok.*;

@RequiredArgsConstructor
@EqualsAndHashCode
@ToString
@Getter
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public final class GenerateTokenRequest {
    private final String keyId;
    private final String keySecret;
}