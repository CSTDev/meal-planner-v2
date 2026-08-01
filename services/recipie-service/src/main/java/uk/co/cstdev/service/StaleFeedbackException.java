package uk.co.cstdev.service;

/**
 * Thrown when feedback is submitted for a (meal_plan_id, recipe_id) pair
 * that no longer has a live row in meal_plan_recipes — e.g. a double-click
 * or a retried request after a dropped response, where the slot was already
 * resolved by an earlier submission. Callers should surface this as an
 * explicit conflict rather than silently no-op'ing.
 */
public class StaleFeedbackException extends RuntimeException {

    public StaleFeedbackException(String message) {
        super(message);
    }
}
