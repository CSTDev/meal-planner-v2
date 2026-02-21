package uk.co.cstdev.data;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FeedbackRepository implements PanacheRepository<UserRecipeInteraction> {

    @Transactional
    public void saveFeedback(UUID userId, UUID recipeId, UUID mealPlanId, FeedbackAction interactionType) {
        UserRecipeInteraction interaction = UserRecipeInteraction.create(userId, recipeId, mealPlanId, interactionType);
        persist(interaction);
    }
}
