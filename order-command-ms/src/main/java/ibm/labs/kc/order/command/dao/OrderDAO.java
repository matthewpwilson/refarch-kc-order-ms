package ibm.labs.kc.order.command.dao;

import java.util.Collection;
import java.util.Optional;

import ibm.labs.kc.order.command.model.CommandOrder;

public interface OrderDAO {

    public void add(CommandOrder order);
    public void update(CommandOrder order);
    public Collection<CommandOrder> getAll();
    public Optional<CommandOrder> getByID(String orderId);

}
