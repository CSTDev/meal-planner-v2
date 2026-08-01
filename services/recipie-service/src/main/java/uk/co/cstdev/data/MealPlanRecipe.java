package uk.co.cstdev.data;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The live contents of a meal plan: one row per recipe currently occupying a
 * slot in the plan (either OFFERED, awaiting a decision, or ACCEPTED). The
 * composite primary key on (meal_plan_id, recipe_id) is what makes it
 * structurally impossible for the same recipe to occupy two slots of the
 * same plan at once.
 */
@Entity
@Table(name = "meal_plan_recipes")
public class MealPlanRecipe extends PanacheEntityBase {

    @EmbeddedId
    public MealPlanRecipeId id;

    @Column(name = "status", nullable = false)
    public String status;

    public MealPlanRecipe() {
    }

    public MealPlanRecipe(MealPlanRecipeId id, String status) {
        this.id = id;
        this.status = status;
    }
}
