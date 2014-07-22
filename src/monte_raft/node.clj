(ns monte-raft.node
  (:require [monte-raft.node.state :as node-state]
            [monte-raft.node.control-worker :refer [control-worker]]
            [monte-raft.node.state-worker :refer [state-worker]]
            [monte-raft.node.leader-worker :refer [leader-worker]]
            [monte-raft.node.worker :as worker]
            [taoensso.timbre :as log]
            [zeromq.zmq :as zmq]
            [clojure.core.async :as async
             :refer [chan close! >! >!! <! <!! go go-loop]]))

(defn node
  "Create a fully functional node, must provide a node-id, context,
  and binding for the control socket (eg: 'ipc:///tmp/node-id.sock' or
  'tcp://*:9001'

   node-id: the unique id of this host

   context: the ZMQ context to be used

   socket-binding: the address to listen on for control messages"
  [node-id control-binding initial-leader]
  (binding [node-state/node-id node-id
            node-state/state (atom nil)
            node-state/term (atom 0)
            node-state/transient-state (atom nil)
            node-state/confirmed (atom false)
            node-state/heartbeat-failure (atom false)]
    (log/debugf "Starting node %s" node-id)
    (log/debugf "will connect to %s as initial leader" initial-leader)
    (let [running-worker (worker/start (control-worker control-binding))]
      (<!! running-worker)
      (log/info "Control has exited. Node shutting down."))
    :terminated))
