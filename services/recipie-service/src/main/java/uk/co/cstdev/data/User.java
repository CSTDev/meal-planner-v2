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
@Table(name = "users")
public class User extends PanacheEntityBase {
    @Id
    public UUID id;

    public String email;

    public String name;

    @Column(name = "created_at")
    Date createdAt;

    public static class Builder {
        private User user;

        private Builder() {
            user = new User();
        }

        public static Builder builder() {
            return new Builder();
        }

        public Builder email(String email) {
            user.email = email;
            return this;
        }

        public Builder id(UUID id) {
            user.id = id;
            return this;
        }

        public Builder name(String name) {
            user.name = name;
            return this;
        }

        public Builder createdAt(Date createdAt) {
            user.createdAt = createdAt;
            return this;
        }

        public User build() {
            return user;
        }
    }
}
