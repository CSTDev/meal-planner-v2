package uk.co.cstdev.data;

import java.util.Date;
import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meal_plans")
public class MealPlan extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id")
    public UUID userId;

    @Column(name = "recipe_source")
    public String recipeSource;

    @Column(name = "created_at")
    public Date createdAt;

    public String status;

    // Builder
    public static class Builder {
        private MealPlan mealPlan;

        private Builder() {
            mealPlan = new MealPlan();
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder userId(UUID userId) {
            mealPlan.userId = userId;
            return this;
        }

        public Builder recipeSource(String recipeSource) {
            mealPlan.recipeSource = recipeSource;
            return this;
        }

        public Builder createdAt(Date createdAt) {
            mealPlan.createdAt = createdAt;
            return this;
        }

        public Builder status(String status) {
            mealPlan.status = status;
            return this;
        }

        public MealPlan build() {
            return mealPlan;
        }
    }
}
