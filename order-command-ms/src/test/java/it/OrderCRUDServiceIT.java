package it;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.Test;

import com.google.gson.Gson;

import ibm.gse.orderms.app.dto.ShippingOrderCreateParameters;
import ibm.gse.orderms.app.dto.ShippingOrderUpdateParameters;
import ibm.gse.orderms.domain.model.order.Address;
import ibm.gse.orderms.domain.model.order.ShippingOrder;
import ibm.gse.orderms.infrastructure.events.OrderCreatedEvent;
import ibm.gse.orderms.infrastructure.events.OrderEvent;
import ibm.gse.orderms.infrastructure.kafka.ApplicationConfig;

public class OrderCRUDServiceIT extends CommonITTest {


    @Test
    public void testCreateSuccess() throws Exception {
        System.out.println("Testing create order " + url);

        Address address = new Address("street", "city", "county", "state", "zipcode");
        ShippingOrderCreateParameters cor = new ShippingOrderCreateParameters();
        cor.setProductID("myProductID");
        cor.setCustomerID("GoodManuf");
        cor.setQuantity(100);
        cor.setPickupDate("2019-01-14T17:48Z");
        cor.setExpectedDeliveryDate("2019-01-15T17:48Z");
        cor.setDestinationAddress(address);
        cor.setPickupAddress(address);

        Response response = makePostRequest(url, new Gson().toJson(cor));
        try {

            int responseCode = response.getStatus();
            assertEquals("Incorrect response code: " + responseCode, 200, responseCode);
            assertTrue(response.hasEntity());
            String responseString = response.readEntity(String.class);

            ShippingOrder o = new Gson().fromJson(responseString, ShippingOrder.class);
            assertNotNull(o.getOrderID());
            assertEquals(cor.getProductID(), o.getProductID());
            assertEquals(cor.getQuantity(), o.getQuantity());
            assertEquals(cor.getPickupDate(), o.getPickupDate());
            assertEquals(cor.getExpectedDeliveryDate(), o.getExpectedDeliveryDate());
        } finally {
            response.close();
        }
    }

    @Test
    public void testCreateEmptyJson() throws Exception {
        Response response = makePostRequest(url, "");
        try {
            int responseCode = response.getStatus();
            assertEquals("Incorrect response code: " + responseCode, responseCode, 400);
        } finally {
            response.close();
        }
    }

    @Test
    public void testCreateBadOrderNegativeQuantity() throws Exception {
        ShippingOrderCreateParameters cor = new ShippingOrderCreateParameters();
        cor.setExpectedDeliveryDate("2019-01-15T17:48Z");
        cor.setPickupDate("2019-01-14T17:48Z");
        cor.setProductID("myProductID");
        cor.setCustomerID("GoodManuf");

        cor.setQuantity(-100);

        Response response = makePostRequest(url, new Gson().toJson(cor));
        try {
            int responseCode = response.getStatus();
            assertEquals("Incorrect response code: " + responseCode, 400, responseCode);
        } finally {
            response.close();
        }
    }

    @Test
    public void testUpdateSuccess() throws Exception {
        String orderID = UUID.randomUUID().toString();
        String putURL = url + "/" + orderID;
        System.out.println("Testing endpoint: " + putURL);

        Properties properties = ApplicationConfig.getProducerProperties("testUpdateSuccess");

        Address addr = new Address("myStreet", "myCity", "myCountry", "myState", "myZipcode");
        ShippingOrder order = new ShippingOrder(orderID, "productId", "custId", 2,
                addr, "2019-01-10T13:30Z",
                addr, "2019-01-10T13:30Z",
                ShippingOrder.PENDING_STATUS);
        OrderEvent event = new OrderCreatedEvent(System.currentTimeMillis(), "1", order);

        try(Producer<String, String> producer = new KafkaProducer<>(properties)) {
            String value = new Gson().toJson(event);
            String key = order.getOrderID();
            ProducerRecord<String, String> record = new ProducerRecord<>(ApplicationConfig.ORDER_TOPIC, key, value);

            Future<RecordMetadata> future = producer.send(record);
            future.get(10000, TimeUnit.MILLISECONDS);
        }

        ShippingOrderUpdateParameters cor = new ShippingOrderUpdateParameters();
        cor.setOrderID(orderID);
        cor.setProductID("myProductID");
        cor.setCustomerID("GoodManuf");
        cor.setQuantity(100);
        cor.setPickupDate("2019-01-14T17:48Z");
        cor.setExpectedDeliveryDate("2019-01-15T17:48Z");
        addr.setCity("NYC");
        cor.setDestinationAddress(addr);
        cor.setPickupAddress(addr);

        int maxattempts = 10;
        boolean ok = false;

        for(int i=0; i<maxattempts; i++) {
            Response response = makePutRequest(putURL, new Gson().toJson(cor));
            if (response.getStatus() == 200) {
                assertTrue(response.hasEntity());
                String responseString = response.readEntity(String.class);
                System.out.println(responseString);

                ShippingOrder o = new Gson().fromJson(responseString, ShippingOrder.class);
                assertEquals(orderID, o.getOrderID());
                assertEquals(cor.getProductID(), o.getProductID());
                assertEquals(cor.getQuantity(), o.getQuantity());
                assertEquals(cor.getPickupDate(), o.getPickupDate());
                assertEquals(cor.getExpectedDeliveryDate(), o.getExpectedDeliveryDate());
                assertEquals(cor.getDestinationAddress(), o.getDestinationAddress());
                assertEquals(cor.getPickupAddress(), o.getPickupAddress());
                ok = true;
            } else {
                Thread.sleep(1000);
            }
        }
        assertTrue(ok);
    }

    @Test
    public void testUpdateDenied() throws Exception {
        String orderID = UUID.randomUUID().toString();
        String putURL = url + "/" + orderID;
        System.out.println("Testing endpoint: " + putURL);

        Properties properties = ApplicationConfig.getProducerProperties("testUpdateSuccess");

        Address addr = new Address("myStreet", "myCity", "myCountry", "myState", "myZipcode");
        ShippingOrder order = new ShippingOrder(orderID, "productId", "custId", 2,
                addr, "2019-01-10T13:30Z",
                addr, "2019-01-10T13:30Z",
                "notPendingStatus");
        OrderEvent event = new OrderCreatedEvent(System.currentTimeMillis(), "1", order);

        try(Producer<String, String> producer = new KafkaProducer<>(properties)) {
            String value = new Gson().toJson(event);
            String key = order.getOrderID();
            ProducerRecord<String, String> record = new ProducerRecord<>(ApplicationConfig.ORDER_TOPIC, key, value);

            Future<RecordMetadata> future = producer.send(record);
            future.get(10000, TimeUnit.MILLISECONDS);
        }

        ShippingOrderUpdateParameters cor = new ShippingOrderUpdateParameters();
        cor.setOrderID(orderID);
        cor.setProductID("myProductID");
        cor.setCustomerID("GoodManuf");
        cor.setQuantity(100);
        cor.setPickupDate("2019-01-14T17:48Z");
        cor.setExpectedDeliveryDate("2019-01-15T17:48Z");
        addr.setCity("NYC");
        cor.setDestinationAddress(addr);
        cor.setPickupAddress(addr);

        Response response = makePutRequest(putURL, new Gson().toJson(cor));
        assertEquals(400, response.getStatus());
    }

   
}
