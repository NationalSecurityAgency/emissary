package emissary.client.response;


import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@XmlAccessorType(XmlAccessType.NONE)
public class TransactionList {

    @XmlElement(name = "host")
    private String host;

    @XmlElement(name = "transactions")
    private SortedSet<String> transactions;

    public TransactionList() {
        transactions = new TreeSet<>();
    }

    public TransactionList(String host, SortedSet<String> transactions) {
        this.host = host;
        this.transactions = transactions;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Set<String> getTransactions() {
        return transactions;
    }

    public void setTransactions(SortedSet<String> transactions) {
        this.transactions = transactions;
    }

    public void addTransaction(String transaction) {
        this.transactions.add(transaction);
    }

    public void dumpToConsole() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n" + getHost() + " :");
        for (String tx : getTransactions()) {
            sb.append("\n         " + tx);
        }
        System.out.print(sb.toString());
    }
}
