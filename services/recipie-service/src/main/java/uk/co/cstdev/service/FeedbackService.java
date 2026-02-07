package uk.co.cstdev.service;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import uk.co.cstdev.data.FeedbackRepository;

@ApplicationScoped
public class FeedbackService {

    @Inject
    private FeedbackRepository feedbackRepository;

    public void processFeedback(UUID userId, UUID recipeId, UUID mealPlanId, String action) {
        feedbackRepository.saveFeedback(userId, recipeId, mealPlanId, action);
    }

}
