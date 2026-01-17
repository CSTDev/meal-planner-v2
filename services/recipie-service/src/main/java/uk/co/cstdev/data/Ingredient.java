package uk.co.cstdev.data;

import jakarta.persistence.Embeddable;

@Embeddable
public class Ingredient {
    public String name;
    public float quantity;
    public String unit;

    public Ingredient() {
    }

    public Ingredient(String name) {
        this.name = name;
        this.quantity = 0;
        this.unit = "";
    }

    public Ingredient(String name, float quantity, String unit) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }
}
