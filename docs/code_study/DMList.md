# DM Implementation
Sapphire paper mentions that every DM has three components: a *proxy*, a *instance manager*, and a *coordinator*. The terminologies you see in [source code](https://github.com/Huawei-PaaS/DCAP-Sapphire/blob/master/sapphire/sapphire-core/src/main/java/sapphire/policy/SapphirePolicyUpcalls.java), however, are different from the ones used in the paper. In source code, they are  called *ClientSidePolicy*, *ServerSidePolicy*, and *GroupPolicy*. (This is my understanding. Correct me if I am wrong.) To implement a DM means to write a class that implements the aforementioned three interfaces.

The following interface definitions were copied from [sapphire source  code](https://github.com/Huawei-PaaS/DCAP-Sapphire/blob/master/sapphire/sapphire-core/src/main/java/sapphire/policy/SapphirePolicyUpcalls.java). Be aware that this is **not the final version**. They are subject to future changes. For example, sapphire paper also mentions `onDestroy`, `onLowMemory`, and `onHighLatency` in interfaces. We probably need to add these functions into the interface later. 

```java
// proxy interface
public interface  SapphireClientPolicyUpcalls extends Serializable {
	public void onCreate(SapphireGroupPolicy group);
	public void setServer(SapphireServerPolicy server);
	public SapphireServerPolicy getServer();
	public SapphireGroupPolicy getGroup();
	public Object onRPC(String method, ArrayList<Object> params) throws Exception;
}

// instance manager interface
public interface SapphireServerPolicyUpcalls extends Serializable {
	public void onCreate(SapphireGroupPolicy group);
	public SapphireGroupPolicy getGroup();
	public Object onRPC(String method, ArrayList<Object> params) throws Exception;
	public void onMembershipChange();
}
	
// coordinator interface
public interface SapphireGroupPolicyUpcalls extends Serializable {
	public void onCreate(SapphireServerPolicy server);
	public void addServer(SapphireServerPolicy server);
	public ArrayList<SapphireServerPolicy> getServers();
	public void onFailure(SapphireServerPolicy server);
	public SapphireServerPolicy onRefRequest();
}
```
# DM Usage
App developers use a DM by passing the name of DM class as a generic type to `SapphireObject` interface. In the following example, `UserManager` declares to use DM `DHTPolicy` to manage its users.

```java
public class UserManager implements SapphireObject<DHTPolicy>, DHTInterface {
	Map<DHTKey, User> users;
	private TagManager tm;
    ...
}
```

Again, this is subject to change. We may use Java annotation down the road.

```java
@SapphireObject
@Proxy(name="DHTClient", 
@InstanceManager(name="DHTServer")
@Coordinator(name="DHTCoordinator")
public class UserManager {
}
```
# DM Injection

![](../images/SapphireOverview.png)

In the above diagram, the dashed arrow lines are remote method invocations between Sapphire objects, the solid arrow lines are local method invocations within a Sapphire object (i.e. within JVM). DMs sit below Sapphire objects. They are essentially proxies for Sapphire objects. When one object calls a remote method on another Sapphire object, the request will first be processed by the DM on client side, (i.e. `DM.Proxy`), the DM on server side (i.e. `DM.InstanceManager`) , and then finally be sent to the real Java object.

As shown in the following diagram, DM consists of many automatically generated components. All these components are wired up by DCAP automatically. Therefore as an App developer, you cannot use normal Java `new` keyword to create a Sapphire object. Sapphire objects have to be created by [`Sapphire._new()`](https://github.com/Huawei-PaaS/DCAP-Sapphire/blob/master/sapphire/sapphire-core/src/main/java/sapphire/runtime/Sapphire.java). Moreover, to invoke a method on an Sapphire object, you must first get the reference to object from OMS - OMS will return a *stub* of the actual Sapphire object.
 
![](../images/DCAP_StubStructure.png)

![](../images/DCAP_RemoteMethodInvocationSequence.png)

# DM List
Here are 26 DMs proposed in Sapphire paper. I will start by writing down my personal thoughts on each DM. The purpose is to trigger discussions within the team so that we can quickly build up consensus on the purpose, the implementation, and the value of each DM. 

I will assign a rate, LOW/MED/HIGH, to each DM to indicate its value to App developers. Again, it is my personal judgement. You are welcome to chime in with your opinions.

![](../images/DMList.png)

## Primitives

### Immutable (N/A) 19 LoC

> Efficient distribution and access for immutable SOs

Quinton: I guess that this means that a local read-only replica of this sapphire object can be instantiated at each client.  All reads go to the local replica, and all writes fail.  That is pretty trivial to implement, and practically very useful. useful. We should check with Irene that this was the intention.

<span style="color:blue">Should *immutable* be a property declared on Sapphire object, or a DM?</span> 

### AtLeastOnceRPC (LOW) 27 LoC
> Automatically retry RPCs for bounded amount of time

This DM will retry failed operations until timeout is reached.

The value of this DM is rated because many SDKs provide similar retry mechanism. App developers have a lot of choices.

Quinton: The implementation of this DM is so simple that I think we should implement it anyway, so that app developers do not need to use a separate SDK for retries, unless they really want to.  Similarly, regarding the comment below, I think that a single retry policy across all operations on a given Sapphire objects is a fine place to start.  We can add per-operation retry configuration as and when actually required by an app.

By the way, to make this DM work properly, we have to make one change to the current DM mechanism:

* Provide Operation Level Support in DM: DMs today are proxies of Sapphire objects in which case DM logics are applied to all operations of a Sapphire object. Retry configuration, however, may vary a lot from operation to operation. DM should provide operation level support.


### KeepInPlace / KeepInRegion / KeepOnDevice (N/A) . 15/15/45 LoC
> Keep SO where it was created (e.g., to access device-specific APIs)

If I understand correctly, by default, SOs cannot move. In order to make a SO mobile, the SO must be managed by some special DM which has the object moving capability. Do we really need a `KeepInPlace` DM? If a SO is not supposed to move, we simply don't associate any DM to this SO. 

Rather than defining *KeepInPlace* as a DM, I feel that it is better to define it as annotation on *Sapphire objects*. If a *Sapphire object* is declared as *KeepInPlace*, then no DM should move it.

<span style="color:blue">Should *KeepInRegion* and *KeepOnDevice* properties declared declared on Sapphire objects, or DM     simplementations?</span>declared on

Quinton:
1. On failure, the DM needs to recreate the sapphire object.  So even KeepInPlace needs to do this, on the same server where the original was.  
2. The DM associated with a Sapphire object (actually class) is in practise a kind of annotation (as discussed above).  So for consistency we should use one mechanism for this, even for KeepInPlace.

## Caching

### ExplicitCaching (LOW) 41 LoC
> Caching w/ explicit push and pull calls from application

<span style="color:blue">Not sure what it is...</span>

### LeaseCaching 133 LoC
> Caching w/ server granting leases, local reads and writes for lease-holder

### WriteThroughCaching (LOW) 43 LoC
> Caching w/ writes serialized to server and stale, local reads

*WriteThroughCache* directs write operations (i.e. mutable operations) onto cached object and through to remote object before confirming write completion. Read operations (i.e. immutable operations) will be invoked on cached object directly.

State changes on remote object caused by one client will not automatically invalidate to cached objects on other clients. Therefore *WriteThroughCache* may contain staled object.

The value of this DM is rated as LOW because 

* Many mutual cache libraries out there. 
* It is not difficult for developers to write their customized client side write through cache. It is not a big deal for them even if we don't provide this DM. 

<span style="color:blue">To make *WriteThroughCache* work, DM needs a mechanism to distinguish *mutable* operations and *immutable* operations.</span>

### ConsistentCaching 98 LoC
> Caching w/ updates sent to every replica for strict consistency

*ConsistentCaching* caches Sapphire object instance on local. *Read* operations will be invoked on local cached object. *Write* operations will be directed to remote Sapphire object. If the Sapphire object has multiple *replicas*, *write* operations will be invoked on all replicas.

* Should updates be propagated to all *replicas*, or be propagated to all *cached objects*? My interpretation is that the updates will be propagated to all replicas. The cached object, therefore, may contain stale data.
* What if the update propagation failed on some replica? 
* Should update propagation occur synchronously or asynchronously?

## Serializability

### SerializableRPC 10 LoC
> Serialize all RPCs to SO with server-side locking

Main logic of this DM occurs on server side. Upon receiving a RPC request, *SerializableRPC* will 1) grab a lock on the Sapphire object, and 2) invoke the RPC on the Sapphire object. All method invocations on the Sapphire object will go through the lock and therefore will be serialized. 

* For Sapphire objects with multiple replicas, should RPCs be serialized across all replicas, or serialized against one specific replica?

### LockingTransactions 81 LoC
> Multi-RPC transactions w/ locking, no concurrent transactions 

*LockingTransactions* uses lock to enforce the serial execution of transactions each of which consists of one or many RPC calls.

* How do users specify transaction boundary? Say, I would like to put operation A and B into one transaction, how do I specify it in DM?
* Are serialization enforced across all Sapphire object replicas, or just against one Sapphire object replica?
* Should this DM take care of state rollback from failed transactions?
* Can users call methods on multiple Sapphire objects in one transaction, e.g. SO1.A() and SO2.B()?

### OptimisticTransactions 92 LoC
> Transactions with optimistic concurrency control, abort on conflict

## Checkpointing

### ExplicitCheckpoint 51 LoC
> App-controlled checkpointing to disk, revert last checkpoint on failure

*ExplicitCheckpoint* allows users to manually checkpoint Sapphire object state via `SO.checkpoint()` API. Sapphire ojbect state will be saved on local host. Users can manually revert Sapphire object to the last checkpoint by calling `SO.revert()` API.

* Description says *revert last checkpoint on failure*. Is this *revert* done by system, or by users manually?
* If *revert* is performed by system automatically, then *on which failures* should the system revert Sapphire object to last checkpoint?

### PeriodicCheckpoint 65 LoC
> Checkpoint to disk every N RPCs, revert to last checkpoint on failure

*PeriodicCheckpoint* periodically, e.g. every N RPCs, saves Sapphire object state on local host. This DM saves Sapphire object before invokes any method on the Sapphire object. If RPC invocation succeeds, result will be returned to client. If RPC invocation fails, Sapphire object will be reverted to last checkpoint, and an error will be thrown to client.

* What is the use case of this DM?
* What if a Sapphire object dies? Will we loose checkpoint data?

### DurableSerializableRPC 29 LoC
> Durable serializable RPCs, revert to last successful RPC on failure

*DurableSerializableRPC* will 1) save Sapphire object state on local host, 2) grab a lock for the RPC call, and 3) invoke RPC on the Sapphire object. If RPC call succeeds, the result will be returned to client. If RPC call fails, the Sapphire object state will be restored, and an error will be thrown back to the client.  

* What is the difference between *DurableSerializableRPC* and *DurableTransactions*? Looks like *DurableSerializableRPC* deals with one RPC call, but *DurableTransactions* deals with multiple RPC calls in one transaction.

### DurableTransactions 112 LoC
> Durably committed transactions, revert to last commit on failure

*DurableTransactions* will 1) save Sapphire object state on local host, 2) grab a lock for the transaction, and 3) invoke multiple *update* operations specified in the transaction boundry on the Sapphire object. If any *update* operation fail, the Sapphire object state will be restored, and an error will be thrown back to the client.  

* Can one transaction involve multiple Sapphire object?
* If the Sapphire object has multiple replicas, should the updates be propagated to other replicas?

## Replication

Quinton: These are essentially all identical except for where the replicas live - in the same cluster/cloud zone, across zones in a cloud region/geo-location, across cloud regions, or across mobile devices (P2P).

### ConsensusRSM-cluster 129 LoC
> Single cluster replicated SO w/ atomic RPCs across at least f + 1 replicas

Quinton: This is essentially the [PAXOS](https://en.wikipedia.org/wiki/Paxos_(computer_science)) or [Raft](https://en.wikipedia.org/wiki/Raft_(computer_science)) consensus algorithms.  It seems impossible to implement even rough approximations of these effectively in 130 lines of code without using existing consensus libraries (e.g. [etcd](https://github.com/coreos/etcd), or [raft](https://raft.github.io/)). 


### ConsensusRSM-Geo 132 LoC
> Geo-replicated SO w/ atomic RPCs across at least f + 1 replicas

Quinton: See above.

### ConsensusRSM-P2P 138 LoC
> SO replicated across client devices w/ atomic RPCs over f + 1 replicas

Quinton: From Irene's thesis: "Peer-to-peer. We built peer-to-peer DMs to support the direct sharing of SOs across client mobile
devices without needing to go through the cloud. These DMs dynamically place replicas on nodes
that contain references to the SO. We implemented the DM using a centralized Coordinator that
attempts to place replicas as close to the callers as possible, without exceeding an application-specified
maximum number of replicas. We show the performance impact of this P2P scheme in Section 2.7."

## Mobility

> Quinton: From Irene's thesis: 
> Code-offloading. The code-offloading DMs are useful for compute-intensive applications. The
CodeOffloading DM supports transparent object migration based on the performance trade-off
between locating an object on a device or in the cloud, while the ExplicitCodeOffloading DM allows
the application to decide when to move computation. The ExplicitCodeOffloading DM gives the
application more control than the automated CodeOffloading DM, but is less transparent because
the SO must interact with the DM.
Once the DK creates the Sapphire Object on a mobile device, the automated CodeOffloading DM
replicates the object in the cloud. The device-side DM Instance Manager then runs several RPCs
locally and asks the cloud-side Instance Manager to do the same, calculating the cost of running
on each side. An adaptive algorithm, based on Q-learning [214], gradually chooses the lowest-cost
option for each RPC. Periodically, the DM retests the alternatives to dynamically adapt to changing
behavior since the cost of offloading computation depends on the type of computation and the
network connection, which can change over time.

Quinton: It seems that CodeOffloading as described above is only suitable for operations that do not read or write state, or only read immutable state.  Otherwise the method invocation cannot be done interchangably on sifferent replicas (e.g. either on a mobile device or a cloud server).  Or else such a code offloading DM needs to be combined with Consistent or Explicit caching.  That way the operation can be executed on any replica, and the resulting state changes propagated to the copies where it was not executed?  Or else the objects need to be migrated to a particular location where all mutating operations need to be invoked.  But for SO's with multiple clients, this seems effectively impossible (e.g. if multiple mobile devices access the same SO, that SO cannot reside on either of the mobile devices - it must reside e.g. on a cloud machine). This whole concept of code offloading vs migration is quite vague and confusing to me.  We need to discuss further with UW/Irene.

### ExplicitMigration 20 LoC
> Dynamic placement of SO with explicit move call from application

Quinton: The intention here is fairly clear, but exactly what calls the application is required to make to move the object are not.  We just need to decide what those calls should look like.  I'm not convinced that this is very useful in practise - ideally placement would be done by a DM, with requirement information provided by the app where necessary (e.g. "place me near these SO's"). I don't really like the idea of placement logic bleeding to the application - that's what Sapphire is supposed to avoid.  This sentiment is echo'd in Irene's thesis above.

Assume that the general intention is as follows: 
1. When the SO is created, the system decides where to place it (initially this could be random - the current implementation just [places it on the first available server](https://github.com/Huawei-PaaS/DCAP-Sapphire/blob/master/sapphire/sapphire-core/src/main/java/sapphire/oms/OMSServerImpl.java#L133)).
2. The application can discover where the system placed it, and request it to be moved to a different server if required.


### DynamicMigration 57 LoC
> Adaptive, dynamic placement to minimize latency based on accesses

Quinton: This one seems quite tricky, but super-valuable.  Here's an initial proposal (although it's not clear how to fit this into 57 LoC.  We should discuss this with UW/Irene.

1. Initial placement is random as above.
2. If the SO is a server only (incoming RPC's only)
  * Server-side of DM keeps track of a sliding window of clients that have accessed it in the recent past (configurable), and a weighted average of latencies to each client (ideally using timestamps on the RPC requests, but that's subject to client-server closck skew, so we need to think more about this - probably need round trip delay from the server to the client instead).

### ExplicitCodeOffloading 49 Loc
> Dynamic code offloading with offload call from application

### CodeOffloading 95 LoC
> Adaptive, dynamic code offloading based on measured latencies

* What exactly is the difference between Offloading and Migration

## Scalability

### LoadBalancedFrontend 53 LoC
> Simple load balancing w/ static number of replicas and no consistency

Quinton: On the client side of the DM for a server Sapphire Object, all RPC's to a server Sapphire Object from a given client are load balanced (using simple round robin load balancing) equally across all replicas of the server Sapphire Object.  More details on Round Robin load balancing are available [here](http://www.jscape.com/blog/load-balancing-algorithms).  Each client side DM instance should randomise the order in which it performs round robin against replicas, so that all clients do not target replicas in the same order, leading to unbalanced load.

* In the initial implementation:
  * load balancing should be implemented inside the DM.  In future load balancing DM's, we can delegate the load balancing to an external load balancing system (e.g. [istio](https://istio.io/)
  * no attempt needs to be made to perform health checking against replicas.  If a replica is dead or slow, calls to it are simply slow or result in errors.  In future load balancing DM's, we can add health checking, retries etc.
  * if new replicas of the SO are added (by some agent outside of the DM), these replicas are simply added to round robin rotation, with no attempt made to backfill additional load to the new, relatively idle replicas.  In future versions, such additional features can be added.
  * a configurable value for the number of concurrent requests supported per replica per should be provided.  This should be enforced on the server side of the DM.  If the number of concurrent requests against a given replica exceeds that number, requests to that server replica should fail (in the server DM) with an appropriate exception (indicating server overload).  In a later version, a configurable length client-side and/or server side queue can be added to deal better with such overload situations..
  
### ScaleUpFrontend 88 LoC
> Load-balancing w/ dynamic allocation of replicas and no consistency

Quinton: As above, but when a given replica reaches it's full capacity (see above), the server-side DM for that replica should create one additional replica.  A given server-side DM instance should not create more than 1 replica per n milliseconds (with n being configurable).  This is to limit the rate at which scale-up can occur.  When the load at a given replica drops to approximately p * (m-2)/m (where m is the current number of replicas, and p is the maximum concurrency setting per replica), then the server-side DM for that replica should remove one replica (randomly chosen).  This is because there are in theory two more replicas than required, so one can be removed.  The number of replicas should not be reduced below 2 (in case one fails).  The aforementioned algorithm is inadequate in a production environment, but is good enough to illustrate the concept.  In later versions, more sophisticated and configurable scale-up and scale-down algorithms can be implmented, and DM's which offload scale-up to external agents (e.g. istio, kubernetes HPA or similar) can be implemented.


### LoadBalancedMasterSlave 177 LoC
> Dynamic allocation of load-balanced M-S replicas w/ eventual consistency

Quinton: As above for read requests (i.e read requests are load balanced across all replicas, with auto-scale-up and scale-down of replicas).  In addition, write requests are all directed to an elected master replica, that (asynchronously) replicates all writes to all replicas (with retries on failures - see AtLeasetOnceRPC as above).  In the inital version, simple master election will be implmented by all client-side DM's selecting the lowest numbered existing replica as the master.  In later versions, more sophisticated master election can be implemented (e.g. by adding health checks, removal of non-responsive master, and using an external etcd/zookper or similar to manage master election in the standard way, e.g. [like this](https://www.projectcalico.org/using-etcd-for-elections/) or [this](http://techblog.outbrain.com/2011/07/leader-election-with-zookeeper/).