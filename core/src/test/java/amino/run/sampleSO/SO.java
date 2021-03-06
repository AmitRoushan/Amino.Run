package amino.run.sampleSO;

/** Created by Venugopal Reddy K on 6/9/18. */
import amino.run.app.MicroService;
import amino.run.policy.mobility.explicitmigration.ExplicitMigrator;
import amino.run.policy.mobility.explicitmigration.MigrationException;
import amino.run.runtime.MicroServiceConfiguration;
import java.net.InetSocketAddress;

@MicroServiceConfiguration(Policies = "amino.run.policy.DefaultPolicy")
public class SO implements MicroService, ExplicitMigrator {
    public Integer i = 0;

    public Integer getI() {
        return i;
    }

    public void setI(Integer value) {
        i = value;
    }

    public void incI() {
        i++;
    }

    public void incI(Integer value) {
        i += value;
    }

    public void decI() {
        i--;
    }

    public Integer getIDelayed() {
        try {
            Thread.sleep(15);
        } catch (InterruptedException e) {

        }
        return i;
    }

    @Override
    public void migrateTo(InetSocketAddress destinationAddr) throws MigrationException {}
}
