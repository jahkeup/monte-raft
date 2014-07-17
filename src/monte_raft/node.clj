(ns monte-raft.node
  (:require [monte-raft.node.socket :as socket]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state :refer [state-worker]]
            [monte-raft.node.macros :refer [on-message-reset!]]
            [zeromq.zmq :as zmq]
            [clojure.core.async :as async
             :refer [chan close! >! >!! <! <!! go go-loop]]))

(defn handle-message
  "Inbound message dispatcher"
  [reply-socket msg]
  (if (msgs/valid-cmd? msg)
    (let [cmd (msgs/to-command-fmt msg)]
      (if (contains? handlers/cmd-handlers cmd)
        (let [handler-func (get handlers/cmd-handlers cmd)]
          (handler-func reply-socket))))))

(defn elect!
  "Force election, sends to all nodes."
  []
  (doall
    (for [remote node-state/cluster]
      (with-open [sock (zmq/socket socket/ctx :req)]
        (zmq/connect sock remote)
        (socket/send-str-timeout sock socket/default-timeout :elect)))))

(defn node-run
  "Run a node task, must be given a map with the current term,
  leader-change-chan (a channel to be communicated over on the event
  of a leader change), and a dead-time after which the leader is
  considered dead."
  [{:keys [term leader-change-chan dead-time] :as
  opts}]
  (loop [term-change false]
    (while (not term-change)
      (if-let [msg (socket/receive-str-timeout
                     socket/control-socket dead-time)]
        (handle-message socket/control-socket msg)))))

(defn node
  "Create a fully functional node, must provide a node-id, context,
  and binding for the control socket (eg: 'ipc:///tmp/node-id.sock' or
  'tcp://*:9001'

   node-id: the unique id of this host

   context: the ZMQ context to be used

   socket-binding: the address to listen on for control messages"
  [node-id context socket-binding]
  (binding [socket/node-control-socket (socket/make-control-listener context socket-binding)
            socket/ctx context
            node-state/state (atom nil)
            node-state/transient-state (atom nil)
            node-state/confirmed (atom false)
            node-state/heartbeat-failure (atom false)]
    (loop [term (atom 0)]
      ;; We're going to block on receiving any leader-change
      ;; messages. Upon leader change we'll also want to reconnect to
      ;; the leader properly.
      (let [leader-change (chan)]
        (node-run {:term term
                   :leader-change-chan leader-change})
        (swap! term inc)))))


