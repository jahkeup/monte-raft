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

