package uk.co.cstdev.service;

import java.util.Date;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import uk.co.cstdev.data.User;

@ApplicationScoped
public class UserService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public User upsert(UUID id, String email, String name) {
        entityManager.createNativeQuery("""
                INSERT INTO users (id, email, name, created_at)
                VALUES (:id, :email, :name, :createdAt)
                ON CONFLICT (id) DO UPDATE
                  SET email = EXCLUDED.email,
                      name  = EXCLUDED.name
                """)
                .setParameter("id", id)
                .setParameter("email", email)
                .setParameter("name", name)
                .setParameter("createdAt", new Date())
                .executeUpdate();

        return User.findById(id);
    }

    @Transactional
    public void delete(UUID id) {
        User user = User.findById(id);
        if (user != null) {
            user.delete();
        }
    }
}
