package uk.co.cstdev.data;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class MealPlanRecipeId implements Serializable {

    @Column(name = "meal_plan_id")
    public UUID mealPlanId;

    @Column(name = "recipe_id")
    public UUID recipeId;

    public MealPlanRecipeId() {
    }

    public MealPlanRecipeId(UUID mealPlanId, UUID recipeId) {
        this.mealPlanId = mealPlanId;
        this.recipeId = recipeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MealPlanRecipeId that)) {
            return false;
        }
        return Objects.equals(mealPlanId, that.mealPlanId) && Objects.equals(recipeId, that.recipeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mealPlanId, recipeId);
    }
}
