package amino.run.policy.scalability;

import amino.run.common.MicroServiceNotFoundException;
import amino.run.common.MicroServiceReplicaNotFoundException;
import amino.run.kernel.common.KernelObjectStub;
import amino.run.policy.DefaultPolicy;
import amino.run.policy.Policy;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

/**
 * Created by SrinivasChilveri on 2/19/18. Simple load balancing w/ static number of replicas and no
 * consistency
 */
public class LoadBalancedFrontendPolicy extends DefaultPolicy {
    public static final int DEFAULT_REPLICA_COUNT = 2;
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 20;

    /** Configurations for LoadBalancedFrontendPolicy */
    public static class Config implements PolicyConfig {
        private int maxConcurrentReq = DEFAULT_MAX_CONCURRENT_REQUESTS;
        private int replicaCount = DEFAULT_REPLICA_COUNT;

        public int getMaxConcurrentReq() {
            return maxConcurrentReq;
        }

        public void setMaxConcurrentReq(int maxConcurrentReq) {
            this.maxConcurrentReq = maxConcurrentReq;
        }

        public int getReplicaCount() {
            return replicaCount;
        }

        public void setReplicaCount(int replicaCount) {
            this.replicaCount = replicaCount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Config config = (Config) o;
            return maxConcurrentReq == config.maxConcurrentReq
                    && replicaCount == config.replicaCount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(maxConcurrentReq, replicaCount);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public @interface LoadBalancedFrontendPolicyConfigAnnotation {
        int maxConcurrentReq() default DEFAULT_MAX_CONCURRENT_REQUESTS;

        int replicaCount() default DEFAULT_REPLICA_COUNT;
    }

    /**
     * LoadBalancedFrontend client policy. The client will LoadBalance among the MicroService Server
     * replica objects. client side DM instance should randomise the order in which it performs
     * round robin against replicas
     *
     * @author SrinivasChilveri
     */
    public static class ClientPolicy extends DefaultPolicy.DefaultClientPolicy {
        private int index;
        protected ArrayList<Policy.ServerPolicy> replicaList;

        @Override
        public Object onRPC(String method, ArrayList<Object> params) throws Exception {
            ServerPolicy server = getCurrentServer();
            return server.onRPC(method, params);
        }

        private synchronized ServerPolicy getCurrentServer() throws Exception {

            if (null == replicaList || replicaList.isEmpty()) {
                // get all the servers which has replicated Objects only once
                // dynamically added replicas are considered later
                replicaList = getGroup().getServers();
                index = (int) (Math.random() * 100);
            }
            index = ++index % replicaList.size();
            return (ServerPolicy) replicaList.get(index);
        }
    }

    /**
     * LoadBalancedFrontend server policy. a configurable value for the number of concurrent
     * requests supported per replica should be provided If the number of concurrent requests
     * against a given replica exceeds that number, requests to that server replica should fail (in
     * the server DM) with an appropriate exception (indicating server overload).
     *
     * @author SrinivasChilveri
     */
    public static class ServerPolicy extends DefaultPolicy.DefaultServerPolicy {
        private static final Logger logger = Logger.getLogger(ServerPolicy.class.getName());
        // we can read from default config or annotations
        protected int maxConcurrentReq = DEFAULT_MAX_CONCURRENT_REQUESTS;
        protected Semaphore limiter;

        @Override
        public void onCreate(Policy.GroupPolicy group) {
            super.onCreate(group);

            Config config = (Config) getPolicyConfig(Config.class.getName());
            if (config != null) {
                this.maxConcurrentReq = config.getMaxConcurrentReq();
            }

            if (this.limiter == null) {
                this.limiter = new Semaphore(this.maxConcurrentReq, true);
            }
        }

        @Override
        public Object onRPC(String method, ArrayList<Object> params) throws Exception {
            boolean acquired = false;
            acquired = limiter.tryAcquire();
            if (acquired) { // concurrent requests count not reached
                try {
                    return super.onRPC(method, params);
                } finally {
                    limiter.release();
                }
            } else {
                logger.warning(
                        "Throwing Exception on server overload on reaching the concurrent requests count"
                                + maxConcurrentReq);
                throw new ServerOverLoadException(
                        "The Replica of the SappahireObject on this Kernel Server Over Loaded on reaching the concurrent requests count "
                                + maxConcurrentReq);
            }
        }
    }

    /**
     * Group policy. creates the configured num of replicas.
     *
     * @author SrinivasChilveri
     */
    public static class GroupPolicy extends DefaultPolicy.DefaultGroupPolicy {
        private static final Logger logger = Logger.getLogger(GroupPolicy.class.getName());
        private int replicaCount = DEFAULT_REPLICA_COUNT; // we can read from config or annotations

        @Override
        public void onCreate(String region, Policy.ServerPolicy server) throws RemoteException {
            server.setToSkipPinning();
            super.onCreate(region, server);

            Config config = (Config) getPolicyConfig(Config.class.getName());
            if (config != null) {
                this.replicaCount = config.getReplicaCount();
            }

            // count is compared below < STATIC_REPLICAS-1 excluding the present server
            int count = 0;
            int numnodes = 0; // num of nodes/servers in the selected region

            /* Creation of group happens when the first instance of microservice is
            being created. Loop through all the kernel servers and replicate the
            microservices on them based on the static replica count */
            try {

                /* Find the current region and the kernel server on which this first instance of
                microservice is being created. And try to replicate the
                microservices in the same region(excluding this kernel server) */
                InetSocketAddress addr = ((KernelObjectStub) server).$__getHostname();
                List<InetSocketAddress> addressList = getAddressList(region);

                /* Create the replicas on different kernelServers belongs to same region*/
                if (addressList != null) {
                    addressList.remove(addr);
                    numnodes = addressList.size();

                    for (count = 0; count < numnodes && count < replicaCount - 1; count++) {
                        replicate(server, addressList.get(count), region);
                    }
                }

                /* If the replicas created are less than the number of replicas configured,
                log a warning message */
                if (count != replicaCount - 1) {
                    logger.severe(
                            "Configured replicas count: "
                                    + replicaCount
                                    + ", created replica count : "
                                    + (count + 1)
                                    + "insufficient servers in region "
                                    + numnodes
                                    + "to create required replicas");
                    throw new Error(
                            "Configured replicas count: "
                                    + replicaCount
                                    + ", created replica count : "
                                    + (count + 1));
                }
            } catch (RemoteException e) {
                logger.severe("Received RemoteException may be oms is down ");
                throw new Error(
                        "Could not create new group policy because the oms is not available.", e);
            } catch (MicroServiceNotFoundException e) {
                throw new Error("Failed to find microservice.", e);
            } catch (MicroServiceReplicaNotFoundException e) {
                throw new Error("Failed to find microservice replica.", e);
            }
        }
    }
}
