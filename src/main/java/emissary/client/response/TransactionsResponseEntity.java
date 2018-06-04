package emissary.client.response;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@XmlRootElement(name = "transactions")
@XmlAccessorType(XmlAccessType.NONE)
public class TransactionsResponseEntity extends BaseResponseEntity {

    private static final long serialVersionUID = 5757691886753092319L;

    @XmlElement(name = "local")
    private TransactionList local = new TransactionList();

    @XmlElement(name = "cluster")
    private final Set<TransactionList> cluster = new HashSet<>();

    public TransactionsResponseEntity() {}

    public TransactionList getLocal() {
        return local;
    }

    public void setLocal(TransactionList local) {
        this.local = local;
    }

    public Set<TransactionList> getCluster() {
        return cluster;
    }

    public void addClusterTransactions(TransactionList transactionList) {
        if (transactionList != null) {
            this.cluster.add(transactionList);
        }
    }

    @Override
    public void append(BaseEntity e) {
        addErrors(e.getErrors());
        if (e instanceof TransactionsResponseEntity) {
            TransactionsResponseEntity pre = this.getClass().cast(e);
            addClusterTransactions(pre.getLocal());
        }
    }

    @Override
    public void dumpToConsole() {
        Stream.concat(cluster.stream(), Stream.of(local)).filter(transactionList -> transactionList.getHost() != null).collect(Collectors.toSet())
                .stream()
                .sorted(Comparator.comparing(TransactionList::getHost)).forEach(transactionList -> {
                    if (transactionList.getTransactions().size() > 0) {
                        transactionList.dumpToConsole();
                    }
                });
    }
}
