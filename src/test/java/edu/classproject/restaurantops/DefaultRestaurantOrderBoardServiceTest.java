package edu.classproject.restaurantops;

import edu.classproject.common.Money;
import edu.classproject.common.OrderStatus;
import edu.classproject.order.InMemoryOrderRepository;
import edu.classproject.order.Order;
import edu.classproject.order.OrderItem;
import edu.classproject.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Team 10 – Restaurant Order Board tests.
 *
 * Happy path:
 *   - getActiveOrders returns only CONFIRMED/PREPARING/READY_FOR_PICKUP orders
 *     for the correct restaurant
 *   - markPreparing transitions CONFIRMED → PREPARING
 *   - markReadyForPickup transitions PREPARING → READY_FOR_PICKUP
 *
 * Edge / failure cases (≥ 2 as required):
 *   1. markPreparing on wrong status throws IllegalStateException
 *   2. markReadyForPickup on wrong status throws IllegalStateException
 *   3. markPreparing on non-existent order throws IllegalArgumentException
 *   4. getActiveOrders for unknown restaurant returns empty list
 *   5. getActiveOrders excludes orders from other restaurants
 *   6. getActiveOrders excludes non-active statuses (DELIVERED, CANCELLED, …)
 */
class DefaultRestaurantOrderBoardServiceTest {

    private static final String REST_A = "rest-A";
    private static final String REST_B = "rest-B";
    private static final String CUST  = "cust-1";

    private OrderRepository repo;
    private DefaultRestaurantOrderBoardService board;

    @BeforeEach
    void setUp() {
        repo  = new InMemoryOrderRepository();
        board = new DefaultRestaurantOrderBoardService(repo);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Order makeOrder(String orderId, String restaurantId, OrderStatus status) {
        OrderItem item = new OrderItem("item-1", "Burger", Money.of(10), 1);
        Order order = new Order(orderId, CUST, restaurantId, List.of(item));
        order.setStatus(status);
        repo.save(order);
        return order;
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getActiveOrders returns CONFIRMED, PREPARING, READY_FOR_PICKUP for the restaurant")
    void getActiveOrders_returnsActiveOrders() {
        makeOrder("ord-1", REST_A, OrderStatus.CONFIRMED);
        makeOrder("ord-2", REST_A, OrderStatus.PREPARING);
        makeOrder("ord-3", REST_A, OrderStatus.READY_FOR_PICKUP);

        List<Order> active = board.getActiveOrders(REST_A);

        assertEquals(3, active.size());
        assertTrue(active.stream().allMatch(o -> REST_A.equals(o.restaurantId())));
    }

    @Test
    @DisplayName("markPreparing transitions CONFIRMED → PREPARING")
    void markPreparing_confirmedOrder_transitionsToPreparing() {
        makeOrder("ord-1", REST_A, OrderStatus.CONFIRMED);

        board.markPreparing("ord-1");

        assertEquals(OrderStatus.PREPARING, repo.findById("ord-1").orElseThrow().status());
    }

    @Test
    @DisplayName("markReadyForPickup transitions PREPARING → READY_FOR_PICKUP")
    void markReadyForPickup_preparingOrder_transitionsToReady() {
        makeOrder("ord-1", REST_A, OrderStatus.PREPARING);

        board.markReadyForPickup("ord-1");

        assertEquals(OrderStatus.READY_FOR_PICKUP, repo.findById("ord-1").orElseThrow().status());
    }

    // -------------------------------------------------------------------------
    // Edge / failure cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Edge 1: markPreparing on non-CONFIRMED status throws IllegalStateException")
    void markPreparing_wrongStatus_throws() {
        makeOrder("ord-1", REST_A, OrderStatus.PREPARING); // already preparing

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> board.markPreparing("ord-1"));

        assertTrue(ex.getMessage().contains("PREPARING"));
        assertTrue(ex.getMessage().contains("CONFIRMED"));
    }

    @Test
    @DisplayName("Edge 2: markReadyForPickup on non-PREPARING status throws IllegalStateException")
    void markReadyForPickup_wrongStatus_throws() {
        makeOrder("ord-1", REST_A, OrderStatus.CONFIRMED); // not yet preparing

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> board.markReadyForPickup("ord-1"));

        assertTrue(ex.getMessage().contains("PREPARING"));
        assertTrue(ex.getMessage().contains("CONFIRMED"));
    }

    @Test
    @DisplayName("Edge 3: markPreparing on unknown orderId throws IllegalArgumentException")
    void markPreparing_unknownOrder_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> board.markPreparing("does-not-exist"));
    }

    @Test
    @DisplayName("Edge 4: getActiveOrders for unknown restaurant returns empty list")
    void getActiveOrders_unknownRestaurant_returnsEmpty() {
        List<Order> active = board.getActiveOrders("unknown-rest");

        assertNotNull(active);
        assertTrue(active.isEmpty());
    }

    @Test
    @DisplayName("Edge 5: getActiveOrders does not return orders from other restaurants")
    void getActiveOrders_excludesOtherRestaurants() {
        makeOrder("ord-A", REST_A, OrderStatus.CONFIRMED);
        makeOrder("ord-B", REST_B, OrderStatus.CONFIRMED);

        List<Order> active = board.getActiveOrders(REST_A);

        assertEquals(1, active.size());
        assertEquals("ord-A", active.get(0).orderId());
    }

    @Test
    @DisplayName("Edge 6: getActiveOrders excludes DELIVERED and CANCELLED orders")
    void getActiveOrders_excludesNonActiveStatuses() {
        makeOrder("ord-delivered",  REST_A, OrderStatus.DELIVERED);
        makeOrder("ord-cancelled",  REST_A, OrderStatus.CANCELLED);
        makeOrder("ord-payFailed",  REST_A, OrderStatus.PAYMENT_FAILED);
        makeOrder("ord-confirmed",  REST_A, OrderStatus.CONFIRMED);

        List<Order> active = board.getActiveOrders(REST_A);

        assertEquals(1, active.size());
        assertEquals("ord-confirmed", active.get(0).orderId());
    }

    @Test
    @DisplayName("Full flow: CONFIRMED → PREPARING → READY_FOR_PICKUP → disappears from active")
    void fullKitchenFlow() {
        makeOrder("ord-1", REST_A, OrderStatus.CONFIRMED);

        // Still on board as CONFIRMED
        assertEquals(1, board.getActiveOrders(REST_A).size());

        board.markPreparing("ord-1");
        assertEquals(OrderStatus.PREPARING, repo.findById("ord-1").orElseThrow().status());

        board.markReadyForPickup("ord-1");
        assertEquals(OrderStatus.READY_FOR_PICKUP, repo.findById("ord-1").orElseThrow().status());

        // Still on board – dispatcher picks it up next
        assertEquals(1, board.getActiveOrders(REST_A).size());

        // Simulate dispatcher moving it to OUT_FOR_DELIVERY (external team)
        repo.findById("ord-1").orElseThrow().setStatus(OrderStatus.OUT_FOR_DELIVERY);

        // No longer on kitchen board
        assertEquals(0, board.getActiveOrders(REST_A).size());
    }
}
