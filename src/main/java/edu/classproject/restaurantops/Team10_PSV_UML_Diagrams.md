# UML Diagrams – Restaurant Order Board
### Team 10 – PSV (Public Static Void) | OOAD Mini Project | PES University

---

## 7. Class Diagram

```mermaid
classDiagram
    class RestaurantOrderBoardService {
        <<interface>>
        +getActiveOrders(restaurantId: String) List~Order~
        +markPreparing(orderId: String) void
        +markReadyForPickup(orderId: String) void
    }

    class DefaultRestaurantOrderBoardService {
        -orderRepository: OrderRepository
        -ACTIVE_STATUSES: Set~OrderStatus~$
        +DefaultRestaurantOrderBoardService(repo: OrderRepository)
        +getActiveOrders(restaurantId: String) List~Order~
        +markPreparing(orderId: String) void
        +markReadyForPickup(orderId: String) void
        -findOrderOrThrow(orderId: String) Order
    }

    class OrderRepository {
        <<interface>>
        +save(order: Order) void
        +findById(orderId: String) Optional~Order~
        +findAll() List~Order~
    }

    class Order {
        -orderId: String
        -customerId: String
        -restaurantId: String
        -items: List~OrderItem~
        -status: OrderStatus
        +setStatus(status: OrderStatus) void
        +totalAmount() Money
        +orderId() String
        +restaurantId() String
        +status() OrderStatus
    }

    RestaurantOrderBoardService <|.. DefaultRestaurantOrderBoardService : implements
    DefaultRestaurantOrderBoardService --> OrderRepository : uses
    OrderRepository ..> Order : manages
```

### Legend

| Notation      | Meaning                          |
|---------------|----------------------------------|
| `<<interface>>`| Java interface                  |
| `<\|..`        | Implements (realization)         |
| `-->`         | Depends on / uses                |
| `$`           | Static member                    |

---

## 8. Sequence Diagram – Kitchen Status Update Flow (`markPreparing`)

```mermaid
sequenceDiagram
    actor KitchenUI
    participant Service as DefaultRestaurantOrderBoardService
    participant Repo as OrderRepository
    participant Order as Order

    KitchenUI->>Service: markPreparing(orderId)
    Service->>Repo: findById(orderId)
    Repo-->>Service: Optional<Order>

    alt Order not found
        Service-->>KitchenUI: throw IllegalArgumentException("Order not found: <orderId>")
    else Order found
        Service->>Service: check order.status() == CONFIRMED
        alt Status != CONFIRMED
            Service-->>KitchenUI: throw IllegalStateException(current + expected statuses)
        else Status == CONFIRMED
            Service->>Order: setStatus(PREPARING)
            Service->>Repo: save(order)
            Service-->>KitchenUI: return (void)
        end
    end
```

---

## State Machine – Order Status Lifecycle (Kitchen Scope)

```mermaid
stateDiagram-v2
    direction LR

    [*] --> CONFIRMED : Order placed & accepted

    CONFIRMED --> PREPARING : markPreparing()
    PREPARING --> READY_FOR_PICKUP : markReadyForPickup()
    READY_FOR_PICKUP --> OUT_FOR_DELIVERY : Dispatcher pickup\n(Team 13)

    OUT_FOR_DELIVERY --> DELIVERED : Delivery complete
    CONFIRMED --> CANCELLED : Cancellation
    PREPARING --> CANCELLED : Cancellation

    note right of CONFIRMED : Active on board
    note right of PREPARING : Active on board
    note right of READY_FOR_PICKUP : Active on board

    DELIVERED --> [*]
    CANCELLED --> [*]

    state "Kitchen Board Scope" as KBS {
        CONFIRMED
        PREPARING
        READY_FOR_PICKUP
    }
```

---

*Team PSV (Public Static Void) – Restaurant Order Board | OOAD Mini Project | PES University*
