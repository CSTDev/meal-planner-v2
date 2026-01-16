package uk.co.cstdev.data;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class RecipeRepository implements PanacheRepository<Recipe> {

    @PersistenceContext
    EntityManager em;

    // Use entity manager for complecated queries if needed e.g.:
    /*
     * String hql = """
     * SELECT new map(
     * c.id as customerId,
     * c.name as customerName,
     * c.email as customerEmail,
     * o.id as orderId,
     * o.orderDate as orderDate,
     * o.totalAmount as orderTotal,
     * p.name as productName,
     * oi.quantity as quantity,
     * oi.price as itemPrice
     * )
     * FROM Customer c
     * JOIN c.orders o
     * JOIN o.orderItems oi
     * JOIN oi.product p
     * WHERE c.id = :customerId
     * ORDER BY o.orderDate DESC
     * """;
     * 
     * return em.createQuery(hql, Map.class)
     * .setParameter("customerId", customerId)
     * .getResultList();
     */
}
