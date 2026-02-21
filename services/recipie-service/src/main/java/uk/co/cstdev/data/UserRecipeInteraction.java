package uk.co.cstdev.data;

import java.time.Instant;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_recipe_interactions")
public class UserRecipeInteraction extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id")
    public UUID userId;

    @Column(name = "recipe_id")
    public UUID recipeId;

    @Column(name = "meal_plan_id")
    public UUID mealPlanId;

    @Column(name = "interaction_type", nullable = false)
    public String interactionType;

    @Column(name = "interaction_at")
    public Instant interactionAt;

    public UserRecipeInteraction() {
    }

    public UserRecipeInteraction(UUID userId, UUID recipeId, UUID mealPlanId, String interactionType) {
        this.userId = userId;
        this.recipeId = recipeId;
        this.mealPlanId = mealPlanId;
        this.interactionType = interactionType;
        this.interactionAt = Instant.now();
    }

    public static UserRecipeInteraction create(UUID userId, UUID recipeId, UUID mealPlanId,
            FeedbackAction interactionType) {
        return new UserRecipeInteraction(userId, recipeId, mealPlanId, interactionType.name());
    }
}
