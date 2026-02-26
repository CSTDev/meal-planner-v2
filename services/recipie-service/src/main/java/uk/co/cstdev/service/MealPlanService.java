package uk.co.cstdev.service;

import java.util.Date;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.MealPlan;
import uk.co.cstdev.data.mealplan.MealPlanRequest;

@ApplicationScoped
public class MealPlanService {

    @Transactional
    public MealPlan createMealPlan(MealPlanRequest request, String userId) {
        MealPlan mealPlan = MealPlan.Builder.builder().userId(UUID.fromString(userId)).createdAt(new Date())
                .recipeSource(request.recipeSource()).status("ACTIVE").build();
        mealPlan.persistAndFlush();
        return mealPlan;
    }
}
