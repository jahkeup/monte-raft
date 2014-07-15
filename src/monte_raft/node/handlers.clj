(ns monte-raft.node.handlers
  (:require [monte-raft.node.messaging :as msgs]
            [monte-raft.node.socket :as socket]
            [monte-raft.node.state :as node-state]
            [zeromq.zmq :as zmq]))


(declare handle-hearbeat handle-term handle-confirm handle-commit
  handle-elect)

(def cmd-handlers "Command handler mapping"
  {:ping handle-hearbeat
   :elect handle-elect
   :confirm handle-confirm
   :commit handle-commit})

(defn handle-heartbeat
  "Respond to a heartbeat message"
  []
  (let [response (first (msgs/responses-for :ping))]
    (zmq/send-str socket/control-socket)))
