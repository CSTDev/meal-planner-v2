package uk.co.cstdev.data;

import java.util.UUID;

public record RecipeFeedback(UUID recipe_id, FeedbackAction action, UUID replacement_recipe_id) {

    public RecipeFeedback(UUID recipe_id, FeedbackAction action) {
        this(recipe_id, action, null);
    }
}
