package amino.run.policy.transaction;

import amino.run.policy.DefaultPolicy;
import java.util.ArrayList;
import java.util.UUID;

/** distributed transaction coordinator default DM set */
public class TwoPCCoordinatorPolicy extends DefaultPolicy {
    /** distributed transaction coordinator client policy */
    public static class TwoPCCoordinatorClientPolicy extends DefaultClientPolicy {}

    /** distributed transaction coordinator server policy */
    public static class TwoPCCoordinatorServerPolicy extends DefaultServerPolicy {
        private transient TwoPCCoordinator coordinator;
        private final transient SandboxProvider sandboxProvider = new AppObjectSandboxProvider();

        @Override
        public void onCreate(GroupPolicy group) {
            super.onCreate(group);
            NonconcurrentTransactionValidator validator =
                    new NonconcurrentTransactionValidator(
                            this.getAppObject(), this.sandboxProvider);
            this.coordinator = new TLS2PCCoordinator(validator);
        }

        @Override
        public Object onRPC(String method, ArrayList<Object> params) throws Exception {
            this.coordinator.beginTransaction();
            UUID transactionId = this.coordinator.getTransactionId();

            ServerUpcalls sandbox = this.sandboxProvider.getSandbox(this, transactionId);

            Object rpcResult;
            try {
                rpcResult = sandbox.onRPC(method, params);
            } catch (Exception e) {
                this.coordinator.abort(transactionId);
                this.sandboxProvider.removeSandbox(transactionId);
                throw new TransactionAbortException("execution had error.", e);
            }

            TransactionManager.Vote vote = this.coordinator.vote(transactionId);

            if (TransactionManager.Vote.YES.equals(vote)) {
                this.coordinator.commit(transactionId);
                this.makeUpdateDurable(sandbox);
                this.sandboxProvider.removeSandbox(transactionId);
                return rpcResult;
            } else {
                this.coordinator.abort(transactionId);
                this.sandboxProvider.removeSandbox(transactionId);
                // todo: to gather the detail of invalidation
                throw new TransactionAbortException("transaction was in invalid state.", null);
            }
        }

        private void makeUpdateDurable(ServerUpcalls sandbox) {
            AppObjectShimServerPolicy shimServerPolicy = (AppObjectShimServerPolicy) sandbox;
            this.appObject = shimServerPolicy.getAppObject();
        }
    }

    /** distributed transaction coordinator group policy */
    public static class TwoPCCoordinatorGroupPolicy extends DefaultGroupPolicy {}
}
