package uk.co.cstdev.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import uk.co.cstdev.data.FeedbackAction;
import uk.co.cstdev.data.MealPlanRecipe;
import uk.co.cstdev.data.MealPlanRecipeId;
import uk.co.cstdev.data.MealPlanRecipeRepository;

@QuarkusTest
public class FeedbackServiceTest {

    @Inject
    FeedbackService feedbackService;

    @InjectMock
    MealPlanRecipeRepository mealPlanRecipeRepository;

    @InjectMock
    uk.co.cstdev.data.FeedbackRepository feedbackRepository;

    @Test
    public void acceptThrowsStaleFeedbackExceptionWhenTheUpdateGuardFailsAfterTheRowWasSeenAtLookup() {
        UUID mealPlanId = UUID.randomUUID();
        UUID recipeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // The row exists when submitFeedback looks it up...
        when(mealPlanRecipeRepository.findByPlanAndRecipe(mealPlanId, recipeId))
                .thenReturn(Optional.of(new MealPlanRecipe(new MealPlanRecipeId(mealPlanId, recipeId), "OFFERED")));
        // ...but by the time accept() writes to it, a concurrent reject has
        // already deleted/changed it (e.g. pool exhaustion), so the
        // update-guard reports no row was actually updated.
        when(mealPlanRecipeRepository.updateStatus(mealPlanId, recipeId, "ACCEPTED"))
                .thenReturn(false);

        assertThrows(StaleFeedbackException.class,
                () -> feedbackService.submitFeedback(userId, mealPlanId, recipeId, FeedbackAction.ACCEPTED, null));
    }
}
