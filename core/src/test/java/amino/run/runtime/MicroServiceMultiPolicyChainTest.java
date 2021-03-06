package amino.run.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import amino.run.app.DMSpec;
import amino.run.app.Language;
import amino.run.app.MicroServiceSpec;
import amino.run.common.*;
import amino.run.policy.DefaultPolicy;
import amino.run.policy.PolicyContainer;
import amino.run.policy.dht.DHTPolicy;
import amino.run.sampleSO.SO;
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class MicroServiceMultiPolicyChainTest extends BaseTest {

    @Rule public ExpectedException thrown = ExpectedException.none();

    private MicroServiceSpec spec;

    @Before
    public void setUp() throws Exception {
        this.serversInSameRegion = false;
        spec =
                MicroServiceSpec.newBuilder()
                        .setLang(Language.java)
                        .setJavaClassName("amino.run.sampleSO.SO")
                        .addDMSpec(
                                DMSpec.newBuilder().setName(DefaultPolicy.class.getName()).create())
                        .addDMSpec(DMSpec.newBuilder().setName(DHTPolicy.class.getName()).create())
                        .create();
        super.setUp(2, spec);
    }

    @Test
    public void testNew_() {
        Object temp = MicroService.new_(SO.class);
        assertNotEquals(null, temp);
    }

    /**
     * This tests the DMs set up in baseTest, and then, tests the createPolicyChain with given spec.
     * It checks whether returned appStub is null.
     *
     * @throws Exception
     */
    // TODO: setUp() adds the DMs and create policy chain already which tests this scenario (hence,
    // tests seem duplicate).
    @Test
    public void testCreatePolicyChain() throws Exception {
        AppObjectStub aos = MicroService.createPolicyChain(spec, "IND", null);
        assertNotNull(aos);
    }

    /**
     * This tests the DMs set up in baseTest, and then, tests createConnectedPolicy in iterative
     * fashion for two DMs which are the same ones as set up in setUp().
     *
     * @throws Exception
     */
    @Test
    public void testCreateConnectedPolicyTwoPolicies() throws Exception {
        List<String> policyNameChain = new ArrayList<String>();
        List<PolicyContainer> processedPolicies = new ArrayList<PolicyContainer>();

        /* Register for a microservice Id from OMS */
        MicroServiceID soid = spiedOms.registerMicroService();

        policyNameChain.add(("amino.run.policy.DefaultPolicy"));
        policyNameChain.add("amino.run.policy.dht.DHTPolicy");
        for (int i = 0; i < 2; i++) {
            MicroService.createConnectedPolicy(
                    null, null, policyNameChain, processedPolicies, soid, spec);
            policyNameChain.remove(0);
        }
        assertEquals(2, processedPolicies.size());
    }

    @After
    public void tearDown() throws Exception {
        System.out.println("Deleting MicroService: " + group.getMicroServiceId());
        super.tearDown();
    }
}
