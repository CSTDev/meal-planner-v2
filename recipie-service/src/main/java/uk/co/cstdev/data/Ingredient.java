package uk.co.cstdev.data;

import jakarta.persistence.Embeddable;

@Embeddable
public class Ingredient {
    public String name;
    public float quantity;
    public String unit;
}
