package com.internship.orderservice.repository;

import com.internship.orderservice.entity.Order;
import com.internship.orderservice.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
                select distinct o from Order o
                left join fetch o.orderItems oi
                left join fetch oi.item
                where o.id in :ids
            """)
    List<Order> findByIdIn(@Param("ids") List<Long> ids);

    @Query("""
                select distinct o from Order o
                left join fetch o.orderItems oi
                left join fetch oi.item
                where o.status in :statuses
            """)
    List<Order> findByStatusIn(@Param("statuses") List<OrderStatus> statuses);
}
