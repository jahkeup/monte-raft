(ns monte-raft.node
  (:require [monte-raft.node.control :as control :refer [control-worker]]
            [monte-raft.node.state :as node-state :refer [state-worker]]
            [monte-raft.node.leader :as leader :refer [leader-worker]]
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
    (go (control-worker control-binding))))
