package ibm.gse.orderms.infrastructure.kafka;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import ibm.gse.orderms.infrastructure.command.events.CreateOrderCommandEvent;
import ibm.gse.orderms.infrastructure.command.events.OrderCommandEvent;
import ibm.gse.orderms.infrastructure.events.Event;
import ibm.gse.orderms.infrastructure.events.EventEmitter;

public class OrderCommandEmitter implements EventEmitter  {
	private static final Logger logger = LoggerFactory.getLogger(OrderCommandEmitter.class);
	
	private KafkaProducer<String, String> kafkaProducer;
	private static OrderCommandEmitter instance;

    public static EventEmitter instance() {
    	synchronized(instance) {
    		if (instance == null) {
    			instance = new OrderCommandEmitter();
    		}
    	}
        return instance;
    }
    
    private OrderCommandEmitter() {
    	Properties properties = ApplicationConfig.getProducerProperties("ordercmd-command-producer");
		properties.put(ProducerConfig.ACKS_CONFIG, "All");
		properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		properties.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-cmd-1");
	    kafkaProducer = new KafkaProducer<String, String>(properties);
	    logger.debug(properties.toString());
        // registers the producer with the broker as one that can use transactions, 
        // identifying it by its transactional.id and a sequence number
        kafkaProducer.initTransactions();
    }
    
	/**
	 * produce exactly one command and ensure all brokers have acknowledged
	 * the command (the replication is done)
	 * 
	 */
	@Override
	public void emit(Event event) throws Exception {

		OrderCommandEvent orderEvent = (OrderCommandEvent)event;
        String key = null;
        String value = null;
        switch (orderEvent.getType()) {
        case OrderCommandEvent.TYPE_CREATE_ORDER:
            key = ((CreateOrderCommandEvent)orderEvent).getPayload().getOrderID();
            value = new Gson().toJson((CreateOrderCommandEvent)orderEvent);
            break;
        case OrderCommandEvent.TYPE_UPDATE_ORDER:
            break;
        default:
            key = null;
            value = null;
        }
        try {
	        kafkaProducer.beginTransaction();
	        ProducerRecord<String, String> record = new ProducerRecord<>(ApplicationConfig.ORDER_COMMAND_TOPIC, key, value);
	        Future<RecordMetadata> send = kafkaProducer.send(record);
	        send.get(ApplicationConfig.PRODUCER_TIMEOUT_SECS, TimeUnit.SECONDS);
	        kafkaProducer.commitTransaction();
        } catch (KafkaException e){
        	kafkaProducer.abortTransaction();
        }
	}

	@Override
	public void safeClose() {
		try {
            kafkaProducer.close();
        } catch (Exception e) {
            logger.warn("Failed closing Producer", e);
        }
		
	}

}
