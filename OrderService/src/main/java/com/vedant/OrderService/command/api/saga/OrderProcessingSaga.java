package com.vedant.OrderService.command.api.saga;

import com.vedant.CommonService.commands.CompleteOrderCommand;
import com.vedant.CommonService.commands.ShipOrderCommand;
import com.vedant.CommonService.commands.ValidatePaymentCommand;
import com.vedant.CommonService.events.OrderCompleteEvent;
import com.vedant.CommonService.events.OrderedShippedEvent;
import com.vedant.CommonService.events.PaymentProcessedEvent;
import com.vedant.CommonService.model.User;
import com.vedant.CommonService.queries.GetUserPaymentDetailsQuery;
import com.vedant.OrderService.command.api.events.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

@Saga
@Slf4j
public class OrderProcessingSaga {

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient QueryGateway queryGateway;


    public OrderProcessingSaga() {
    }

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    private void handle(OrderCreatedEvent event) {
        log.info("OrderCreatedEvent in Saga for Order Id : {}",
                event.getOrderId());

        GetUserPaymentDetailsQuery getUserPaymentDetailsQuery
                = new GetUserPaymentDetailsQuery(event.getUserId());
        log.info("event.getUserID:{}",event.getUserId());
        User user;

        try {
            user = queryGateway.query(
                    getUserPaymentDetailsQuery,
                    ResponseTypes.instanceOf(User.class)
            ).join();
            assert user != null;
            ValidatePaymentCommand validatePaymentCommand
                    = ValidatePaymentCommand
                    .builder()
                    .cardDetails(user.getCardDetails())
                    .orderId(event.getOrderId())
                    .paymentId(UUID.randomUUID().toString())
                    .build();

            commandGateway.sendAndWait(validatePaymentCommand);

        } catch (Exception e) {
            log.error(e.getMessage());
            //Start the Compensating transaction
//            cancelOrderCommand(event.getOrderId());
        }



    }

    @SagaEventHandler(associationProperty = "orderId")
    private void handle(PaymentProcessedEvent event) {
        log.info("PaymentProcessedEvent in Saga for Order Id : {}",
                event.getOrderId());
        try {

            if(true)
                throw new Exception();

            ShipOrderCommand shipOrderCommand
                    = ShipOrderCommand
                    .builder()
                    .shipmentId(UUID.randomUUID().toString())
                    .orderId(event.getOrderId())
                    .build();
            commandGateway.send(shipOrderCommand);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void handle(OrderedShippedEvent event) {

        log.info("OrderShippedEvent in Saga for Order Id : {}",
                event.getOrderId());

        CompleteOrderCommand completeOrderCommand
                = CompleteOrderCommand.builder()
                .orderId(event.getOrderId())
                .orderStatus("APPROVED")
                .build();

        commandGateway.send(completeOrderCommand);
    }

    @SagaEventHandler(associationProperty = "orderId")
    @EndSaga
    public void handle(OrderCompleteEvent event) {
        log.info("OrderCompletedEvent in Saga for Order Id : {}",
                event.getOrderId());
    }

}
