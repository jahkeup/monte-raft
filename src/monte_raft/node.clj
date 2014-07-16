(ns monte-raft.node
  (:require [monte-raft.node.socket :as socket]
            [monte-raft.node.messaging :as msgs]
            [monte-raft.node.handlers :as handlers]
            [monte-raft.node.state :as node-state]
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

(defmacro on-message-reset!
  "Upon recieving a message from channel, reset! the `set-atom' to
  `set-value'. Non-blocking"
  [channel set-atom set-value]
  `(go (do (<! ~channel)
        (reset! ~set-atom ~set-value))))

(defn state-run
  "Go thead designed to run in the background until a message is sent
  over the stop-chan channel. "
  [{:keys [update-socket stop-chan check-period]}]
  (let [should-stop (atom false)]
    (on-message-reset! stop-chan should-stop true)
    (while (not @should-stop)
      ;; Potential problems here in the future if the windows don't align..
      (if-let [new-state (socket/receive-str-timeout
                           update-socket check-period)]
        ;; Blindly set transient state to the received state.
        (reset! node-state/transient-state new-state)))
    :exiting))

(defn node
  "Create a fully functional node, must provide a node-id, context,
  and binding for the control socket (eg: 'ipc:///tmp/node-id.sock' or
  'tcp://*:9001'"
  [node-id context binding]
  (binding [socket/node-control-socket (socket/make-control-listener context binding)
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


