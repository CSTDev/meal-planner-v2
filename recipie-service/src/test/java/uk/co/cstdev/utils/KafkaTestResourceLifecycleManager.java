package uk.co.cstdev.utils;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;

public class KafkaTestResourceLifecycleManager implements QuarkusTestResourceLifecycleManager {

    @Override
    public Map<String, String> start() {
        Map<String, String> env = new HashMap<>();
        Map<String, String> incomingRecipes = InMemoryConnector.switchIncomingChannelsToInMemory("recipes");
        Map<String, String> outgoingScrapeRequests = InMemoryConnector
                .switchOutgoingChannelsToInMemory("scrape-requests");
        env.putAll(incomingRecipes);
        env.putAll(outgoingScrapeRequests);
        return env;
    }

    @Override
    public void stop() {
        InMemoryConnector.clear();
    }
}