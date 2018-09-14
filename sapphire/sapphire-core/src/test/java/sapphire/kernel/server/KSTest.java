package sapphire.kernel.server;

import static org.powermock.api.mockito.PowerMockito.mock;
import static sapphire.common.UtilsTest.extractFieldValueOnInstance;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import sapphire.app.SO;
import sapphire.app.SapphireObject;
import sapphire.app.stubs.SO_Stub;
import sapphire.common.BaseTest;
import sapphire.common.SapphireObjectID;
import sapphire.common.SapphireUtils;
import sapphire.kernel.common.KernelOID;
import sapphire.kernel.common.KernelObjectFactory;
import sapphire.kernel.common.KernelObjectStub;
import sapphire.kernel.common.KernelRPC;
import sapphire.kernel.common.KernelRPCException;
import sapphire.kernel.common.ServerInfo;
import sapphire.policy.DefaultSapphirePolicy;
import sapphire.policy.util.ResettableTimer;
import sapphire.runtime.Sapphire;

/** Created by Vishwajeet on 11/9/18. */
@RunWith(PowerMockRunner.class)
@PrepareForTest({
    KernelServerImpl.class,
    Sapphire.class,
    KernelObjectFactory.class,
    LocateRegistry.class,
    SapphireUtils.class
})
public class KSTest extends BaseTest {
    @Rule public ExpectedException thrown = ExpectedException.none();

    public static class DefaultSO extends SO implements SapphireObject {}

    public static class Group_Stub extends DefaultSapphirePolicy.DefaultGroupPolicy
            implements KernelObjectStub {
        sapphire.kernel.common.KernelOID $__oid = null;
        java.net.InetSocketAddress $__hostname = null;
        int $__lastSeenTick = 0;

        public Group_Stub(sapphire.kernel.common.KernelOID oid) {
            this.$__oid = oid;
        }

        public sapphire.kernel.common.KernelOID $__getKernelOID() {
            return this.$__oid;
        }

        public java.net.InetSocketAddress $__getHostname() {
            return this.$__hostname;
        }

        public void $__updateHostname(java.net.InetSocketAddress hostname) {
            this.$__hostname = hostname;
        }

        public int $__getLastSeenTick() {
            return $__lastSeenTick;
        }

        public void $__setLastSeenTick(int lastSeenTick) {
            this.$__lastSeenTick = lastSeenTick;
        }
    }

    public static class Server_Stub extends DefaultSapphirePolicy.DefaultServerPolicy
            implements KernelObjectStub {
        KernelOID $__oid = null;
        InetSocketAddress $__hostname = null;
        int $__lastSeenTick = 0;

        public Server_Stub(KernelOID oid) {
            this.oid = oid;
            this.$__oid = oid;
        }

        public KernelOID $__getKernelOID() {
            return $__oid;
        }

        public InetSocketAddress $__getHostname() {
            return $__hostname;
        }

        public void $__updateHostname(InetSocketAddress hostname) {
            this.$__hostname = hostname;
        }

        public int $__getLastSeenTick() {
            return $__lastSeenTick;
        }

        public void $__setLastSeenTick(int lastSeenTick) {
            this.$__lastSeenTick = lastSeenTick;
        }
    }

    @Before
    public void setUp() throws Exception {
        super.setUp(Server_Stub.class, Group_Stub.class);
        SapphireObjectID sapphireObjId =
                spiedOms.createSapphireObject("sapphire.kernel.server.KSTest$DefaultSO");
        soStub = (SO_Stub) spiedOms.acquireSapphireObjectStub(sapphireObjId);
        client =
                (DefaultSapphirePolicy.DefaultClientPolicy)
                        extractFieldValueOnInstance(soStub, "$__client");
        so = ((SO) (server1.sapphire_getAppObject().getObject()));
    }

    @Test
    public void testHeartbeat() throws Exception {
        Field field = PowerMockito.field(KernelServerImpl.class, "ksHeartbeatSendTimer");
        field.set(KernelServerImpl.class, mock(ResettableTimer.class));
        ServerInfo srvinfo = new ServerInfo(new InetSocketAddress("127.0.0.1", 10001), "IND");
        KernelServerImpl.startheartbeat(srvinfo);
    }

    @Test
    public void testMakeKernelRPC() throws Exception {
        String method = "public java.lang.Integer sapphire.app.SO.getI()";
        ArrayList<Object> params = new ArrayList<Object>();
        KernelRPC rpc = new KernelRPC(server1.$__getKernelOID(), method, params);
        thrown.expect(KernelRPCException.class);
        spiedKs1.makeKernelRPC(rpc);
    }
}