package com.greenpaw.ai.runtime;

import java.util.Map;

public record AgentCard(
        String type,
        String title,
        Map<String, Object> payload
) {
}
