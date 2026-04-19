package edu.classproject.restaurantops;

import edu.classproject.common.OrderStatus;
import edu.classproject.order.Order;
import edu.classproject.order.OrderRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Team 10 – Restaurant Order Board
 *
 * Depends on: OrderRepository (from edu.classproject.order)
 *
 * Active orders are those the kitchen still owns:
 *   CONFIRMED → PREPARING → READY_FOR_PICKUP
 *
 * Allowed status transitions:
 *   CONFIRMED        → PREPARING        (markPreparing)
 *   PREPARING        → READY_FOR_PICKUP (markReadyForPickup)
 *
 * Any other transition is rejected with IllegalStateException.
 */
public class DefaultRestaurantOrderBoardService implements RestaurantOrderBoardService {

    /** Statuses visible on the board (kitchen still owns these). */
    static final Set<OrderStatus> ACTIVE_STATUSES = Set.of(
            OrderStatus.CONFIRMED,
            OrderStatus.PREPARING,
            OrderStatus.READY_FOR_PICKUP
    );

    private final OrderRepository orderRepository;

    public DefaultRestaurantOrderBoardService(OrderRepository orderRepository) {
        if (orderRepository == null) {
            throw new IllegalArgumentException("orderRepository must not be null");
        }
        this.orderRepository = orderRepository;
    }

    /**
     * Returns all active orders for the given restaurant, ordered by orderId
     * for stable listing (no timestamp available on Order).
     */
    @Override
    public List<Order> getActiveOrders(String restaurantId) {
        if (restaurantId == null || restaurantId.isBlank()) {
            throw new IllegalArgumentException("restaurantId must not be blank");
        }
        return orderRepository.findAll().stream()
                .filter(o -> restaurantId.equals(o.restaurantId()))
                .filter(o -> ACTIVE_STATUSES.contains(o.status()))
                .sorted((a, b) -> a.orderId().compareTo(b.orderId()))
                .collect(Collectors.toList());
    }

    /**
     * Transitions an order from CONFIRMED → PREPARING.
     * Throws IllegalStateException for any other current status.
     */
    @Override
    public void markPreparing(String orderId) {
        Order order = findOrderOrThrow(orderId);
        if (order.status() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException(
                    String.format("Cannot mark PREPARING: order %s is in status %s (expected CONFIRMED)",
                            orderId, order.status()));
        }
        order.setStatus(OrderStatus.PREPARING);
        orderRepository.save(order);
    }

    /**
     * Transitions an order from PREPARING → READY_FOR_PICKUP.
     * Throws IllegalStateException for any other current status.
     */
    @Override
    public void markReadyForPickup(String orderId) {
        Order order = findOrderOrThrow(orderId);
        if (order.status() != OrderStatus.PREPARING) {
            throw new IllegalStateException(
                    String.format("Cannot mark READY_FOR_PICKUP: order %s is in status %s (expected PREPARING)",
                            orderId, order.status()));
        }
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        orderRepository.save(order);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Order findOrderOrThrow(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    }
}
