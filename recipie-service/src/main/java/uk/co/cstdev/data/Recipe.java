package uk.co.cstdev.data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.JoinColumn;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipes")
public class Recipe extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    public String url;
    public String title;
    public String description;

    @ElementCollection
    @CollectionTable(name = "recipe_ingredients", joinColumns = @JoinColumn(name = "recipe_id"))
    public List<Ingredient> ingredients;

    public List<String> instructions;
    public int prepTimeMinutes;
    public int cookTimeMinutes;
    public int servings;
    public List<String> tags;
    public String imageUrl;
    public Date createdAt;
    public Date scrapedAt;

    // Builder
    public static class Builder {
        private Recipe recipe;

        private Builder() {
            recipe = new Recipe();
            recipe.createdAt = new Date();
            recipe.scrapedAt = new Date();
            recipe.ingredients = new ArrayList<>();
            recipe.instructions = new ArrayList<>();
            recipe.tags = new ArrayList<>();
        }

        public static Builder recipe() {
            return new Builder();
        }

        public Builder id(UUID id) {
            recipe.id = id;
            return this;
        }

        public Builder id(String id) {
            recipe.id = UUID.fromString(id);
            return this;
        }

        public Builder url(String url) {
            recipe.url = url;
            return this;
        }

        public Builder title(String title) {
            recipe.title = title;
            return this;
        }

        public Builder description(String description) {
            recipe.description = description;
            return this;
        }

        public Builder ingredient(String name, float quantity, String unit) {
            Ingredient ingredient = new Ingredient();
            ingredient.name = name;
            ingredient.quantity = quantity;
            ingredient.unit = unit;
            recipe.ingredients.add(ingredient);
            return this;
        }

        public Builder ingredient(Ingredient ingredient) {
            recipe.ingredients.add(ingredient);
            return this;
        }

        public Builder ingredients(List<Ingredient> ingredients) {
            recipe.ingredients = new ArrayList<>(ingredients);
            return this;
        }

        public Builder instruction(String instruction) {
            recipe.instructions.add(instruction);
            return this;
        }

        public Builder instructions(List<String> instructions) {
            recipe.instructions = new ArrayList<>(instructions);
            return this;
        }

        public Builder tag(String tag) {
            recipe.tags.add(tag);
            return this;
        }

        public Builder tags(List<String> tags) {
            recipe.tags = new ArrayList<>(tags);
            return this;
        }

        public Builder prepTime(int minutes) {
            recipe.prepTimeMinutes = minutes;
            return this;
        }

        public Builder cookTime(int minutes) {
            recipe.cookTimeMinutes = minutes;
            return this;
        }

        public Builder servings(int servings) {
            recipe.servings = servings;
            return this;
        }

        public Builder imageUrl(String imageUrl) {
            recipe.imageUrl = imageUrl;
            return this;
        }

        public Builder createdAt(Date createdAt) {
            recipe.createdAt = createdAt;
            return this;
        }

        public Builder scrapedAt(Date scrapedAt) {
            recipe.scrapedAt = scrapedAt;
            return this;
        }

        public Recipe build() {
            return recipe;
        }
    }
}