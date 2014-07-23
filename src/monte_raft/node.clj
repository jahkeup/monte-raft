(ns monte-raft.node
  (:require [monte-raft.node.state :as node-state]
            [monte-raft.node.control-worker :refer [control-worker]]
            [monte-raft.node.state-worker :refer [state-worker]]
            [monte-raft.node.leader-worker :refer [leader-worker]]
            [monte-raft.node.macros :refer [until-receive-from]]
            [monte-raft.node.worker :as worker]
            [monte-raft.client :as client]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]
            [clojure.core.async :as async
             :refer [chan close! >! >!! <! <!! go go-loop]]))

(defn node
  "Start a node

   node-id: the unique id of this host

   opts: node config option map, create with
  monte-raft.node.state/make-node-options"
  [node-id {:keys [control-binding] :as opts}]
  (log/tracef "Spawning node '%s' using options: \n%s" node-id
    (with-out-str (clojure.pprint/pprint opts)))
  (binding [node-state/node-id node-id
            node-state/state (atom nil)
            node-state/term (atom 0)
            node-state/transient-state (atom nil)
            node-state/confirmed (atom false)]
    (log/debugf "Starting node %s" node-id)
    (let [running-worker (worker/start (control-worker control-binding))]
      (<!! running-worker)
      (log/info "Control has exited. Node shutting down."))
    :terminated))

(defn start-system
  "Based on the cluster defined in the node.state, start all the nodes and run."
  []
  (client/start-nrepl)
  (doall
    (for [node-id (keys @node-state/cluster)]
      (let [node-options (node-id @node-state/cluster)]
        (worker/start (node node-id (:control-binding node-options)))
        (until-receive-from (client/nrepl-comm-sub)
          (Thread/sleep 1000))
        (client/stop-nrepl)))))


