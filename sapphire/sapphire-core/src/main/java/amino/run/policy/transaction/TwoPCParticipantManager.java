package amino.run.policy.transaction;

import amino.run.policy.SapphirePolicy;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** DCAP transaction participant manager */
public class TwoPCParticipantManager implements TwoPCParticipants, Serializable {
    private Set participants =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<SapphirePolicy.SapphireClientPolicy, Boolean>());

    @Override
    public void register(SapphirePolicy.SapphireClientPolicy cohort) {
        if (!this.participants.contains(cohort)) {
            this.participants.add(cohort);
        }
    }

    @Override
    public Collection<SapphirePolicy.SapphireClientPolicy> getRegistered() {
        return new ArrayList<SapphirePolicy.SapphireClientPolicy>(this.participants);
    }
}