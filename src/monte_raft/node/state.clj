(ns monte-raft.node.state)

(def ^:dynamic state
  "This is the state that the consensus is operating for." nil)

(def ^:dynamic transient-state
  "The in-between state that is pending confirmation and committal." nil)

(def ^:dynamic cluster "Cluster node addresses"
  (atom []))

(def ^:dynamic node-options
  "Node options that are used to configure certain aspects"
  (atom {}))

(def ^:dynamic global-nodes-state
  "YES BAD BAD BAD, we're using this for statstics." (atom {}))

(def ^:dynamic heartbeat-failure
  "Channel recieves message on a heartbeat failure." (atom false))

(def ^:dynamic confirmed
  "Has the transient state been confirmed?" (atom false))

(defn confirmable? "Is the current state confirmable?" []
  (not (nil? @transient-state)))

(defn confirmed! "Mark transient state confirmed" []
  (reset! confirmed true))

(defn confirmed? "Has the current transient state been confirmed?" []
  (true? @confirmed))

(defn commit!
  "Terminate state chain, update state and clear transient and
  confirmation flag" []
  (reset! state @transient-state)
  (reset! confirmed false)
  (reset! transient-state nil))
